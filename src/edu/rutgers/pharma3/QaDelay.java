package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

/** A Quality Assurance Delay serves as a delay unit that identifies
    some portion of the input as "bad", and removes it from the channel 
    before it's offered to the downstream consumer.
 */
public class QaDelay extends Delay {

    /** It is expected that it only returns numbers within [0:1] range */
    final double discardProb, reworkProb;
    
    /** Pill counts */
    double badResource = 0, reworkResource=0, releasedGoodResource=0;
    public double getBadResource() { return badResource; }
    public double getReleasedGoodResource() { return releasedGoodResource; }
    public double getReworkResource() { return reworkResource; }

    //final DiscardSink discardSink;
    final MSink discardSink;

    /** @param typicalBatch A Batch of the appropriate type. Size does not matter.
     */
    public QaDelay(SimState state, Batch typicalBatch,  double _discardProb, double _reworkProb) {
	super(state, typicalBatch);
	setOfferPolicy(Provider.OFFER_POLICY_FORWARD);
	discardProb = _discardProb;
	reworkProb= _reworkProb;
	discardSink = new MSink( state, typicalBatch);
	//addReceiver(discardSink);
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
    
    /*
    class DiscardSink extends Msink {
	DiscardSink(SimState state, Resource typical) {
	    super(state,typical);
	}
	
	public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	}
    }  
    */

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

	boolean willDiscard=false, willRework=false;
	if ( discardProb + reworkProb >0) {
	    boolean isBad = state.random.nextBoolean(discardProb + reworkProb);
	    if (isBad) {
		willRework = state.random.nextBoolean( reworkProb/(discardProb + reworkProb));
		willDiscard = !willRework;
	    }
	}

	boolean z =
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
	
	return z;

    }

    int reworkBatches=0, badBatches=0,    releasedBatches=0;
 

    /** Still under processing + at the output */
    double hasBatchesOnBothSides() {
	return getTotal() + getAvailable();
    }

    public String hasBatches() {
	String s = "" + (long)getTotal();
	if (getAvailable()>0) s += "+"+(long)getAvailable();
	return s;
    }

    
}
