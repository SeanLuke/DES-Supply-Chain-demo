package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

/** A Quality Assurance Delay serves as a delay unit that identifies
    some portion of the input as "bad", and removes it from the channel    
 */
class QaDelay extends Delay {

    /** It is expected that it only returns numbers within [0:1] range */
    AbstractDistribution faultyPortionDistribution;
    double badResource = 0, releasedGoodResource=0;
    

    
    public QaDelay(SimState state, Resource typical, AbstractDistribution _faultyPortionDistribution) {
	super(state, typical);
	faultyPortionDistribution = _faultyPortionDistribution;
    }

    /** This is a wrapper over the standard Provider.offerReceiver(),
	which reduces the amount of available stuff (resource) due
	to damage by mice and weevils, or other adverse effects.	    
	FIXME: This will be consistent with Abhisekh writeup
	if there is a separate step() (and offerReceiver()) call
	for each token. However, the distribution will be slightly
	off if multiple tokens are processed in a single call.
    */
    protected boolean offerReceiver(Receiver receiver, double atMost) {
	CountableResource cr = (CountableResource) resource;
	double amt = cr.getAmount();
	long faulty = Math.round( amt *faultyPortionDistribution.nextDouble());
	if (faulty < 0) faulty = 0;
	if (faulty > amt) faulty = (long) amt;	    
	badResource += faulty;
	releasedGoodResource += (amt - faulty);
	cr.decrease(faulty);
	return super.offerReceiver(receiver, atMost-faulty);
    }

    /** Still under processing + at the output */
    double hasOnBothSides() {
	return getDelayed() + getAvailable();
    }
    
}
