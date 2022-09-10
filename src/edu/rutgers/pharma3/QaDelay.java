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
public class QaDelay extends //Delay
SimpleDelay
    implements Reporting, Reporting.HasBatches
{

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


    private Timer faultRateIncrease = new Timer();

    /** This is used by a disruptor to reduce the quality of the
	products produced by this unit over a certain time
	interval. This only should be used for fungible products
	(CountableResource); for Batch products, it's preferable to
	use the similar method in ProdDelay, so that the "poor
	quality" will be associated with the manufacturing date,
	rathere than the QA date.
    */
     void setFaultRateIncrease(double x, Double _untilWhen) {
	faultRateIncrease.setValueUntil(x,_untilWhen);
    }
 
  
    final Resource prototype;

    
    /** @param typicalBatch A Batch of the appropriate type (size does not matter), or a CountableResource
	@param _faultyPortionDistribution If non-null, then _discardProb and double _reworkProb must be zero, and vice versa.
     */
    public QaDelay(SimState state, Resource typicalBatch,  double _discardProb, double _reworkProb, AbstractDistribution _faultyPortionDistribution) {
	super(state, typicalBatch);
	prototype = typicalBatch;
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

	if (para.get("faulty")==null) {
	    throw  new IllegalInputException("Found no value for  " + para.name +".faulty");
	} else if (para.get("faulty").size()==1) {
	    faultyProb = para.getDouble("faulty");
	} else {
	    faultyPortionDistribution = para.getDistribution("faulty",state.random);
	}
	double reworkProb = para.getDouble("rework", 0.0);	    

	if (faultyPortionDistribution !=null && faultyProb+reworkProb!=0) throw new IllegalInputException("For " + para.name +", specify either faulty portion distribution or faulty+rework probabilities, but not both!");    
    
	QaDelay qaDelay = new QaDelay(state, outResource, faultyProb, reworkProb, faultyPortionDistribution);			      
	//qaDelay.setDelayDistribution(para.getDistribution("qaDelay",state.random));
	//Double delayTime = para.getDouble("qaDelay", null);
	//if (delayTime==null) throw new IllegalInputException("Missing value for " + para.name +".qaDelay in config file!");
	//System.out.println("Creating QaDelay." + para.name + "(" + delayTime+")");
	//qaDelay.setDelayTime(delayTime);
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
	are to offer to the receiver. The reduction can be due to the QA inspection
	removing some poor quality items, or due to 
	to damage by mice and weevils, or to other adverse effects.	

	//FIXME: this method has the assumption that the
	Receiver will take everything offered to it. If this assumption
	does not hold, the repeated offers will repeatedly find
	bad items in the already-checked pool. This can be fixed
	by creating a separate Queue for the already-checked stuff,
	and passing it to Receiver.accept() calls.
       		
    */
    protected boolean offerReceiver(Receiver receiver, double atMost) {

	if (Demo.verbose)   System.out.println(getName() + ".offerReceiver(" +receiver+", " + atMost+")");
	
	boolean z;

	if (faultyPortionDistribution!=null) {
	    double amt, faulty;

	    double r = faultyPortionDistribution.nextDouble();
	    if (r<0) r=0;
	    if (r>1) r=1;

	    if (entities == null) {
		CountableResource cr = (CountableResource) resource;
		amt = Math.min( cr.getAmount(), atMost);		
		if (amt==0) return false; // this happens sometimes, triggered by SimpleDelay.step()

		double t = state.schedule.getTime();
		double rEffective = Math.min(r + faultRateIncrease.getValue(t), 1.0);

		
		faulty = Math.round( amt * rEffective);
		// The faulty product is destroyed, so we decrease the resource now
		cr.decrease(faulty);
		z = super.offerReceiver(receiver, atMost-faulty);
	    } else {
		// throw new IllegalArgumentException("pharma3.QaDelay with faultyPortionDistribution only works with fungibles, because we don't support variable-size batches!");
		
		Batch e = (Batch)entities.getFirst();
		amt = e.getContentAmount();
		faulty = Math.round( amt * r);
		e.getContent().decrease(faulty);
		z = super.offerReceiver(receiver, e);
		entities.remove(e);		// FIXME: do I need to manually remove e from entities?
	    }


	    if (!z) throw new IllegalArgumentException("QaDelay cannot be used with a receiver ("+receiver.getName()+") that refuses offers.  amt="+amt+", atMost=" +atMost+", faulty="+faulty);
	    
	    badResource +=  faulty;
	    releasedGoodResource += (amt-faulty);

	    if (faulty>0) 	    badBatches++;
	    if (faulty<amt)	    releasedBatches ++;

	    //System.out.println("F=" + faulty +", G=" + (amt-faulty));
	    
	} else {
	    if (entities == null) throw new IllegalArgumentException("pharma3.QaDelay with faultyProb only works with Batches!");

	    Batch b = (Batch)entities.getFirst();
	    double amt = b.getContentAmount();
	    
	    boolean willDiscard=false, willRework=false;
	    double dp = discardProb + b.getLot().increaseInFaultRate;

	    if ( dp + reworkProb >0) {
		boolean isBad = state.random.nextBoolean(dp + reworkProb);
		if (isBad) {
		    willRework = state.random.nextBoolean( reworkProb/(dp + reworkProb));
		    willDiscard = !willRework;
		}
	    }

	    z =
		willRework ? super.offerReceiver(sentBackTo, b):
		willDiscard? super.offerReceiver(discardSink, b):
		super.offerReceiver(receiver, b);


	    if (!z) throw new IllegalArgumentException("The expectation is that the receivers for QaDelay " + getName() + " never refuse a batch");
	    
	    //z =
	    //willRework ? super.offerReceiver(sentBackTo, atMost):
	    //	willDiscard? super.offerReceiver(discardSink, atMost):
	    //	super.offerReceiver(receiver, atMost);
		
	    //ArrayList<Resource> lao = getLastAcceptedOffers();
	    //if (lao==null || lao.size()!=1) throw new IllegalArgumentException("Unexpected result from shipOutDelay.getLastAcceptedOffers()");
	    //Batch b = (Batch)lao.get(0);
	    
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

	    // System.out.println(getName() + ".offerReceiver(" +receiver+", " + atMost+"): reworkBatches="+reworkBatches+", badBatches=" + badBatches+", releasedBatches=" + releasedBatches);

	}

	if (whomToWakeUp != null) {
	    double t = state.schedule.getTime();
	    state.schedule.scheduleOnce(t+ 1e-5, whomToWakeUp);
	}

	
	return z;

    }

    long reworkBatches=0, badBatches=0,    releasedBatches=0;
 

    /** Still under processing + at the output */
    double hasBatchesOnBothSides() {
	return getDelayed() + getAvailable();
    }

    public String hasBatches() {
	String s = "" + (long)getDelayed();
	if (getAvailable()>0) s += "+"+(long)getAvailable();
	return s;
    }

    public String report() {

	String s = "(in QA= " +  hasBatches() +	" ba; discarded="+(long)badResource  +
	" ("+badBatches+" ba)";
	if (reworkProb>0) s+= "; rework="+(long)reworkResource +
				      " ("+reworkBatches+" ba)";

	s += "; good=" + (long)releasedGoodResource+" ("+releasedBatches+" ba))";
	return s;
	
    }

    /**  binary search for the median of the faultyPortionDistribution */
    double findMedian() {

	//AbstractContinousDistribution u = (AbstractContinousDistribution)faultyPortionDistribution;
	Uniform u = (Uniform)faultyPortionDistribution;
	
	final double eps = 1e-6;
	
	double a=0, b=1, c;
	if (u.cdf(a)>0.5 ||
	    u.cdf(b)<0.5) throw new IllegalArgumentException("Bad cdf for " + faultyPortionDistribution);
	do {
	    c = (a+b) /2;
	    double val = u.cdf(c);
	    if (val == 0.5) return c;
	    else if (val < 0.5) a = c;
	    else b = c;
	} while(b-a > eps);
	return c;
    }

    /** Stats for planning 
	@param return The average output/input ratio
     */
    double computeAlpha() {

	double mean = 0;
	if (faultyPortionDistribution!=null) {
	    if (faultyPortionDistribution instanceof ParaSet.MyUniform) {
		ParaSet.MyUniform u = (ParaSet.MyUniform)faultyPortionDistribution;
		mean = (u.getMin() + u.getMax())/2;
	    } else {
		// FIXME: if the distribution is not symmetric, the mean
		// is not the same as median
		mean =  findMedian();
	    }
	} else {
	    mean = discardProb;
	}
	return 1-mean;
	
    }
    
}
