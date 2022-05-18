package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import  edu.rutgers.util.*;


/** A Quality Assurance Delay serves as a delay unit that identifies
    some portion of the input as "bad", and removes it from the channel 
    before it's offered to the downstream consumer. Optionally, it
    can also direct some portion of the input to the "rework" receiver.
 */
public class QaDelay extends Delay {

    /** If non-null, we reschedule this object at the end of each 
	offerReceiver. This can be used to get the QaDelay automatically
	reloaded a bit later.
    */
    private Steppable whomToWakeUp = null;

    public void setWhomToWakeUp(Steppable x)  { whomToWakeUp = x; }
    
    /** This is non-null if we have item-by-item testing, with partial
      discard. If it is present, then discardProb and reworkProb. It
      must be zero. is expected that it only returns numbers within
      [0:1] range */
    final AbstractDistribution faultyPortionDistribution;

    /** If any of these is non-zero, then we do whole-batch test-and-discard.
	In this case, faultyPortionDistribution must be null.
     */
    final double discardProb, reworkProb;
    
    /** Pill counts */
    double badResource = 0, reworkResource=0, releasedGoodResource=0;
    public double getBadResource() { return badResource; }
    public double getReleasedGoodResource() { return releasedGoodResource; }
    public double getReworkResource() { return reworkResource; }
    
    long badResourceBatches = 0, releasedGoodResourceBatches=0;
 
    final MSink discardSink;

    /** @param typicalBatch A Batch of the appropriate type (size does not matter), or a CountableResource
	@param _faultyPortionDistribution If non-null, then _discardProb and double _reworkProb must be zero, and vice versa.
     */
    public QaDelay(SimState state, Resource typicalBatch,  double _discardProb, double _reworkProb, AbstractDistribution _faultyPortionDistribution) {
	super(state, typicalBatch);
	setName("QaDelay("+typicalBatch.getName()+")");
	setOfferPolicy(Provider.OFFER_POLICY_FORWARD);
	discardProb = _discardProb;
	reworkProb= _reworkProb;
	faultyPortionDistribution = _faultyPortionDistribution;
  	discardSink = new MSink( state, typicalBatch);
	//addReceiver(discardSink);

	if (faultyPortionDistribution!=null) {
	    if (discardProb!=0 || reworkProb !=0) throw new IllegalArgumentException("Cannot set both faultyPortionDistribution and discardProb/reworkProb on the same QaDelay!");
	} 
    }

    /** Creates a QaDelay based on the parameters from a specified ParaSet */
    static public QaDelay mkQaDelay(ParaSet para, SimState state, Resource outResource) throws IllegalInputException {	
	// See if "faulty" in the config file is a number or
	// a distribution....
	double faultyProb=0;	
	AbstractDistribution faultyPortionDistribution=null;
	try {
	    faultyPortionDistribution = 
		para.getDistribution("faulty",state.random);
	} catch(IllegalInputException ex) {
	    faultyProb = para.getDouble("faulty");
	}
	double reworkProb = para.getDouble("rework", 0.0);	    

	if (faultyPortionDistribution !=null && faultyProb+reworkProb!=0) throw new IllegalInputException("For " + para.name +", specify either faulty portion distribution or faulty+rework probabilities, but not both!");    
    
	QaDelay qaDelay = new QaDelay(state, outResource, faultyProb, reworkProb, faultyPortionDistribution);			      
	qaDelay.setDelayDistribution(para.getDistribution("qaDelay",state.random));
	return qaDelay;
    }

    Receiver sentBackTo=null;


    /** Call this method if the QA process, in addtion to discarding 
	some items, can also send some items back to the factory
	for reprocessing.
	@param  _sentBackTo Where to send some of the faulty products for rework.
     */
    void setRework( Receiver _sentBackTo) {
	sentBackTo=_sentBackTo;
    }
    
    /** This is a wrapper over the standard Provider.offerReceiver(),
	which reduces the amount of available stuff (resource) we
	are to offer to the receiver. The reduction is due to 
	to damage by mice and weevils, or to other adverse effects.	

	//FIXME: this method has the assumption that the
	Receiver will take everything offered to it. If this assumption
	does not hold, the repeated offers will repeatedly find
	bad items in the already-checked pool. This can be fixed
	by creating a separate Queue for the already-checked stuff,
	and passing it to Receiver.accept() calls.
       		
    */
    protected boolean offerReceiver(Receiver receiver, double atMost) {

	if (Demo.verbose) System.out.println(getName() + ".offerReceiver(" +receiver+", " + atMost+")");
	
	boolean z;

	if (faultyPortionDistribution!=null) {
	    double amt, faulty;

	    double r = faultyPortionDistribution.nextDouble();
	    if (r<0) r=0;
	    if (r>1) r=1;

	    if (entities == null) {
		CountableResource cr = (CountableResource) resource;
		amt = Math.min( cr.getAmount(), atMost);
		faulty = Math.round( amt * r);
		// The faulty product is destroyed, so we decrease the resource now
		cr.decrease(faulty);
		z = super.offerReceiver(receiver, atMost-faulty);
	    } else {
		Batch e = (Batch)entities.getFirst();
		amt = e.getContentAmount();
		faulty = Math.round( amt * r);
		e.getContent().decrease(faulty);
		z = super.offerReceiver(receiver, e);
	    }
	    badResource +=  faulty;
	    if (faulty>0) 	    badBatches++;
	    releasedGoodResource += (amt-faulty);
	    releasedBatches ++;
	    
	} else {
	    if (entities == null) throw new IllegalArgumentException("pharma3.QaDelay with faultyProb only works with Batches!");
  
	    boolean willDiscard=false, willRework=false;
	    if ( discardProb + reworkProb >0) {
		boolean isBad = state.random.nextBoolean(discardProb + reworkProb);
		if (isBad) {
		    willRework = state.random.nextBoolean( reworkProb/(discardProb + reworkProb));
		    willDiscard = !willRework;
		}
	    }
	    
	    z =
		willRework ? super.offerReceiver(sentBackTo, atMost):
		willDiscard? super.offerReceiver(discardSink, atMost):
		super.offerReceiver(receiver, atMost);
		
	    ArrayList<Resource> lao = getLastAcceptedOffers();
	    if (lao==null || lao.size()!=1) throw new IllegalArgumentException("Unexpected result from shipOutDelay.getLastAcceptedOffers()");
	    Batch batch = (Batch)lao.get(0);
	    double amt = batch.getContentAmount();
	    
	    if (willRework) {
		reworkResource += amt;
		reworkBatches++;
	    } else if (willDiscard) {
		badResource +=  amt;
		badBatches++;
	    } else {
		releasedGoodResource += amt;
		releasedBatches++;
	    }
	}

	if (whomToWakeUp != null) {
	    double t = state.schedule.getTime();
	    state.schedule.scheduleOnce(t+ 1e-5, whomToWakeUp);
	}

	
	return z;

    }

    int reworkBatches=0, badBatches=0,    releasedBatches=0;
 

    /** Still under processing + at the output */
    double hasBatchesOnBothSides() {
	return getDelayed() + getAvailable();
    }

    public String hasBatches() {
	String s = "" + (long)getDelayed();
	if (getAvailable()>0) s += "+"+(long)getAvailable();
	return s;
    }

    
}
