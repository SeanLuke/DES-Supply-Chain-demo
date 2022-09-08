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

/** A ThrottleQueue controls the behavior of a SimpleDelay, to ensure that it holds
    exactly one batch at a time, and the processing time of each batch is drawn
    from a specified distribution. It is attached in front of the SimpleDelay
    it controls.
 */
public class ThrottleQueue extends sim.des.Queue
    implements     Named//,	    Reporting
{

    private AbstractDistribution delayDistribution;
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
	//	delay.setDelayTimeNoClearing( delayTime);

	
	delay.setDelayTime( delayTime);
			    
	double t = state.schedule.getTime();       
	SimpleDelay sd = (SimpleDelay)receiver;

	if (Demo.verbose && getName().indexOf("PackagingMat")>=0 && t>0) 		    
	    	System.out.println("At " + t +", "+getName() + ".offerReceiver(" + receiver.getName()+","+atMost+") (cap="+
				   sd.getCapacity() +", delay="+sd.getDelayTime()+"), had=" + hasBatches());

	boolean z=super.offerReceiver(receiver, atMost);

	if (Demo.verbose && getName().indexOf("PackagingMat")>=0) {
	    System.out.println("At " + t +", "+getName() + " offered to " + receiver.getName()+", result=" + z +"; now has=" + hasBatches());
	}
			    
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
	//System.out.println(getName() + "=" + hasBatches());
    }

    /** Just for debugging */
    /*   
    public boolean accept(Provider provider, Resource r, double atLeast, double atMost) {
	double t = state.schedule.getTime();
	if (System.verbose && getName().indexOf("PackagingMat")>=0) 
	    System.out.println("At " + t +", "+getName() + " accepting " + r+", had=" + hasBatches());
	boolean z = super.accept( provider, r, atLeast, atMost);
	if (System.verbose && getName().indexOf("PackagingMat")>=0) 
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
     
