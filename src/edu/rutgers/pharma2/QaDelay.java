package  edu.rutgers.pharma2;

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
    final AbstractDistribution faultyPortionDistribution;
    
    /* If 0, then the faultyPortionDistribution is applied to the entire amount given,
       and the number is interpreted as a percentage of the amount that's discarded.
       Otherwise, every batch is processed separately, and is either accepted or 
       rejected, with the probability given by the same distribution.
     */
    final double batchSize;

    double badResource = 0, reworkResource=0, releasedGoodResource=0;
    public double getBadResource() { return badResource; }
    public double getReleasedGoodResource() { return releasedGoodResource; }
    public double getReworkResource() { return reworkResource; }
 
    public QaDelay(SimState _state, Resource typical, double _batchSize, AbstractDistribution _faultyPortionDistribution) {
	super(_state, typical);
	batchSize = _batchSize;
	faultyPortionDistribution = _faultyPortionDistribution;
    }

    double reworkFraction=0;
    Receiver sentBackTo=null;


    /** Call this method if the QA process, in addtion to discarding 
	some items, can also send some items back to the factory
	for reprocessing.
	@param _reworkFraction Portion of the faulty products that are
	sent back to the factory, rather than discarded. This must be 
	within the range [0,1]
	@param  _sentBackTo Where to send some of the faulty products for rework.
     */
    void setReworkFraction(double _reworkFraction, Receiver _sentBackTo) {
	reworkFraction=_reworkFraction;
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

	
	CountableResource cr = resource;
	double amt = Math.min( cr.getAmount(), atMost);
	final double amt0 = amt;
	
	double faulty=0, rework=0;

	if (batchSize==0) { // apply "faulty percentage"

	    double r = faultyPortionDistribution.nextDouble();
	    if (r<0) r=0;
	    if (r>1) r=1;
	    
	    faulty = Math.round( amt * r);
	    if (reworkFraction>0) {
		rework =  Math.round( faulty*reworkFraction);
		faulty -= rework;
	    }
	} else { // apply "fault probability" to each batch
	    double a = Math.min(amt, atMost);
	    while(a > 0) {
		double b = Math.min(a, batchSize);
		a -= b;
		double r = faultyPortionDistribution.nextDouble();
		if (r>0.5) {
		    if (getState().random.nextBoolean(reworkFraction)) {
			rework += b;
		    } else {
			faulty += b;
		    }
		}
	    }
	}
	double good = amt - faulty - rework;
	
	badResource += faulty;
	reworkResource += rework;
	releasedGoodResource += good;
	// The faulty product is destroyed, so we decrease the resource now
	cr.decrease(faulty);
	if (rework>0) {
	    // This decreases the resource by rework
	    super.offerReceiver(sentBackTo, rework);
	}
	double atMost1 = Math.max(0,  atMost-faulty-rework);
	//if (Demo.verbose) System.out.println("QaDelay: good="+good+" (out of "+amt0+", f="+faulty+", r="+rework+"), offer: " + cr+", atMost1=" + atMost1);
	// and this, too, decreases the resource 
	return super.offerReceiver(receiver, atMost1);

    }

    /** Still under processing + at the output */
    double hasOnBothSides() {
	return getDelayed() + getAvailable();
    }

    public String has() {
	String s = "" + getDelayed();
	if (getAvailable()>0) s += "+"+getAvailable();
	return s;
    }

    
}
