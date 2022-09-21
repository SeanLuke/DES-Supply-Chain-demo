package  edu.rutgers.pharma3;

import java.util.Vector;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;
import edu.rutgers.pharma3.Disruptions.Disruption;

/** A ThrottleQueue is normally used in tandem with a
    SimpleDelay. Taken together, they model a production
    stage where only 1 batch of the material can be processed
    at a given time, and not-yet-processed material is waiting
    for its turn outside of the "work area". (The ThrottleQueue 
    represents the waiting area, and the SimpleDelay, the
    work area). This can model, for example, a bread oven
    that can bake one batch of loaves at a time, or a forklift
    that can move one pallet of stuff between warehouses.

    <P>Each batch can have its own processing time (drawn from
    a random distribution), but, since only one batch is processed
    at a time, we can model the "work area" with a SimpleDelay
    (modifying its delay time before processing each batch), rather
    than a Delay. This is supposed to be more computationally
    efficient.
   
    <P>In the  ThrottleQueue-SimpleDelay tandem, the ThrottleQueue
    controls the behavior of the SimpleDelay, to ensure
    that it holds exactly one batch at a time, and the processing time
    of each batch is drawn from a specified distribution. It is
    attached in front of the SimpleDelay it controls.

    <p>The ThrottleQueue sets the capacity of the SimpleDelay to 1,
    and sets itself as the slackProvider for the delay, so that every
    time the delay is finished with a batch ("the bread has been baked")
    it pulls one more batch ("a batch of raw loaves") from the queue. 
    Additionally, when something is put into the Queue, its offerReceiver
    checks whether the delay is empty ("the oven is idle"), and if it is, 
    it offers resource to the delay. These two mechanism, in combination,
    ensure that the delay ("the oven") is never idle, as long as there is
    anything to be processed.
 */
public class ThrottleQueue extends sim.des.Queue
    implements     Named//,	    Reporting
{

    private AbstractDistribution delayDistribution;

    public AbstractDistribution getDelayDistribution() {
	return delayDistribution;
    }
    
    /** The capacity-1 SimpleDelay for which the ThrottleQueue serves as an
	input buffer */
    final private SimpleDelay delay;
    
/** Creates a Queue to be attached in front of the Delay, and
	links it up appropriately. This is done so that we can model a
	production facility (or a transportation service, etc) which can
	handle no more than a given number of batches at any given
	time.
	
	@param delay Models the production step whose capacity we want to restrict. (For example, a bread oven with space for exactly 1 batch of loaves, or a truck that has space for exactly 1 shipping container of stuff).
	
	@param cap The max number of batches that the production unit (the Delay object) can physically handle simultaneously. (Usually, 1).

	@return A Queue object into which one can put any number of "raw" batches, where they will sit and wait for the production facility to grab them whenever it's ready. 
     */
    public  ThrottleQueue(SimpleDelay _delay, double cap, AbstractDistribution _delayDistribution) {
	super(_delay.getState(), _delay.getTypical());
	delay = _delay;
	delayDistribution = _delayDistribution;
	setOffersImmediately(true);
	setName("TQ for " + delay.getName());
	if (delay.getTypical() instanceof Entity) {
	    if (cap!=1) throw new IllegalArgumentException("Entity-based ThrottleQueue must have cap=1 for its delay"); 
	} 
	    
	
	delay.setCapacity(cap);
	
	addReceiver(delay);
	delay.setSlackProvider(this);	
    }

    void setDelayDistribution(AbstractDistribution d) {
	delayDistribution = d;
    }
    
    /** This method is called whenever a batch is put into the ThrottleQueue
	(due to the immediatOffers flag), and whenever the throttled SimpleDelay
	becomes empty (through the slackProvider mechanism). When it is 
	called, it verifies that the throttled SimpleDelay is in fact
	empty, and if it is, sets a new randomly picked delay time,
	and then puts a batch into the delay. */
    protected boolean offerReceiver(Receiver receiver, double atMost) {
	if (receiver != delay)  throw new IllegalArgumentException("Wrong receiver for ThrottleQueue");

	/*
	if (!(delay.getTypical() instanceof Entity) && (atMost!=delay.getCapacity())) {
	    throw new IllegalArgumentException("Wrong batch size (given="+
					       receiver.getAmount()+", expected=" +
					       delay.getCapacity());
	}
	*/

	if (delay.getDelayed() > 0) return false; // the SimpleDelay is not empty

	
	double delayTime = Math.abs(delayDistribution.nextDouble());
	
	delay.setDelayTime( delayTime);
			    
	//double t = state.schedule.getTime();       

	boolean z=super.offerReceiver(receiver, atMost);
			    
	return z;
    }


    public String hasBatches() {
	String s = "" + (long)getAvailable();
	if (delay instanceof Reporting.HasBatches) {
	    s += "+(" + ((Reporting.HasBatches)delay).hasBatches() +")";
	}
	return s;
    }

    public void step(SimState state) throws IllegalArgumentException {
	super.step(state);
    }

    /** Just for debugging */
    /*   
    public boolean accept(Provider provider, Resource r, double atLeast, double atMost) {
	double t = state.schedule.getTime();
	if (Demo.verbose && getName().indexOf("PackagingMat")>=0) 
	    System.out.println("At " + t +", "+getName() + " accepting " + r+", had=" + hasBatches());
	boolean z = super.accept( provider, r, atLeast, atMost);
	if (Demo.verbose && getName().indexOf("PackagingMat")>=0) 
	System.out.println("At " + t +", "+getName() + " accepted " + r+"? Result=" + z +"; has=" + hasBatches());
	return z;
    }
    */
    
    /** Just for debugging */
    /*
    public boolean offerReceivers() {
	double t = state.schedule.getTime();
	System.out.println("At " + t +", "+getName() + " doing offerReceivers(), had=" + hasBatches());
	boolean z = super. offerReceivers();
	System.out.println("At " + t +", "+getName() + " done offerReceivers(). Result=" + z +"; has=" + hasBatches());
	return z;
 	
    }
    */

    /** Simply exposes offerReceivers() to classes in this package. Can be
	used to "prime the system". */
    protected boolean offerReceivers() {
	return super.offerReceivers();
    }
   	

    
}
     
