package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

/** Dispatch store sits at the end of our model
  */
class DispatchStorage extends sim.des.Queue implements Reporting {

    /** This simply serves as a time to indicate that we have 
	called for an empty truck to come and pick the entire
	content of the warehouse */
    //    Delay emptyTruckDelay;

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

    EmptyTruckTimer emptyTruckTimer;

    AbstractDistribution  outDelayDistribution;
    
    DispatchStorage(SimState state, String name,
	      Resource outResource,
		    int maximum,		   
		    AbstractDistribution  _outDelayDistribution
		   )
    {
	super(state, outResource);
	setName(name);
	setCapacity(maximum);
       
	outDelayDistribution = _outDelayDistribution;
	consumer = new MSink( state,  outResource);
	//CountableResource truck = new CountableResource("Truck",0);
	//emptyTruckDelay = new Delay(state, truck);
	//	emptyTruckDelay.setDelayDistribution(outDDelayDistribution);
    }

    /** If there is enough stuff to call for a truck, call it
     */
    public void step​(SimState state) {
	final int threshold = 50;
	if (getAvailable()>threshold && emptyTruckTimer==null) {

	    double nextTime = state.schedule.getTime() +  Math.abs(outDelayDistribution.nextDouble());
	    
	    emptyTruckTimer = new EmptyTruckTimer();
	    state.schedule.scheduleOnce(nextTime, emptyTruckTimer);
	    System.out.println("At t=" + state.schedule.getTime() + ", " + getName() + " calls for a truck; ETA=" + nextTime);
	}
    }

    /** This is triggered by the time when the empty truck has come
	to take everything away */
    void truckIsHere() {
	System.out.println("At t=" + state.schedule.getTime() + ", " + getName() + " loading to a customer's truck");
	provide( consumer);
	emptyTruckTimer=null;
    }
    
    public String report() {
	return "[DispatchStorage."+getName()+", has "+ getAvailable()+ " units. Ever shipped: " + consumer.everConsumed + "]";
    }

    
}
    
