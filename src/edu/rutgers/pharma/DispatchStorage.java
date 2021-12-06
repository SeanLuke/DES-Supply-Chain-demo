package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;

import sim.des.*;

import edu.rutgers.util.*;

/** Dispatch store sits at the end of our model.
  */
class DispatchStorage extends sim.des.Queue implements Reporting {

    /** The outside consumer, who does not have a lot of properties */
    MSink consumer;

    /** One schedules this thing once when ordering an empty truck */
    class EmptyTruckTimer implements Steppable {
	/** This is triggered when the empty truck comes to take
	    the entire content of the warehouse
	 */
	public void step(SimState state) {
	    DispatchStorage.this.truckIsHere();
	}
    }

    /** When null, it means that the truck has not been called; when non-null,
	the truck has been called, but has not arrived yet. */
    EmptyTruckTimer emptyTruckTimer;

    AbstractDistribution  outDelayDistribution;
    final double threshold;
    
    DispatchStorage(SimState state, String name, Config config,
	      Resource outResource ) throws IllegalInputException
    {
	super(state, outResource);
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	setCapacity(para.getDouble("capacity"));
	threshold = para.getDouble("threshold");
	       
	outDelayDistribution = para.getDistribution("delay",state.random);
	consumer = new MSink( state,  outResource);
    }

    /** If there is enough stuff to call for a truck, and a truck
	has not been called yet, call it now.
     */
    public void step​(SimState state) {

	if (getAvailable()>threshold && emptyTruckTimer==null) {

	    double nextTime = state.schedule.getTime() +  Math.abs(outDelayDistribution.nextDouble());
	    
	    emptyTruckTimer = new EmptyTruckTimer();
	    state.schedule.scheduleOnce(nextTime, emptyTruckTimer);
	    if (((Demo)state).verbose) System.out.println("At t=" + state.schedule.getTime() + ", " + getName() + " calls for a truck; ETA=" + nextTime);
	}
    }

    /** This is triggered by the time when the empty truck has come
	to take everything away. The method offloads the entire
	content of the warehouse, and turns off the timer, so that
	it can be turned on again when needed.
    */
    void truckIsHere() {
	if (((Demo)state).verbose) System.out.println("At t=" + state.schedule.getTime() + ", " + getName() + " loading to a customer's truck; available="+getAvailable());
	provide( consumer);
	emptyTruckTimer=null;
    }
    
    public String report() {
	return "[DispatchStorage."+getName()+", has "+ getAvailable()+ " units. Ever shipped: " + consumer.everConsumed + "]";
    }

    
}
    
