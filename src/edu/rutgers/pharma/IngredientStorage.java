package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

/** Ingredient storage */
class IngredientStorage extends sim.des.Queue implements Reporting {

    /** Enables access to the resource, so that we can pass it as an argument
	in the accept() calls in downstream users 
    */
    //    CountableResource getResource() { return resource; }
    


    
    /** (4*24,5*24) */
    AbstractDistribution supplierDelayDistribution;
    /** It seems like one needs a Provider object to call Delay.accept()
	with,  	so let's create one. 
     */
    //Provider dummyProvider;
    /** Models the ordering of the ingredient from the infinite supply
	pool, and the delay involving in processing this order (say,
	shipping by truck) */
    Delay supplierDelay;
    
    IngredientStorage(SimState state, String name, CountableResource resource, int maximum) {
	super(state, resource);
	setCapacity(maximum);
	setName(name);
	supplierDelayDistribution = new Uniform(4*24,5*24,state.random);
	supplierDelay = new Delay(state,resource);
	supplierDelay.setDelayDistribution(supplierDelayDistribution);
	supplierDelay.addReceiver(this);
    }

    public String report() {
	return "[IngStor."+getName()+"(cap="+getCapacity()+"), has " + resource.getAmount() + " units of " + getTypical().getName() + ". EverReceived="+
	    everReceieved     +"]";
    }


    private int batchesOrdered = 0;

    /** Returns true if all plasced orders have been fulfilled by the
	supplier (i.e. there are no outstanding orders) */
    private boolean nothingInSupplyQueue() {
	return supplierDelay.getSize()==0;
	//return (batchesOrdered == 0);
    }
    
    /** If our storage is not full, and no supply truck is currently
	on the way already, the storage orders another supply truck
	(thru supplierDelay).
     */
    public void step​(sim.engine.SimState state) {
	final double threshold = 350;
	// FIXME: Abhisekh likes to "over-order" (asking for 800 units),
	// but I am not doing it because I don't have proper support for
	// a possible overflow. 
	if (nothingInSupplyQueue() &&
	    resource.getAmount() < threshold) {
	    double neededAmount = getCapacity() - resource.getAmount();
	    if (((Demo)state).verbose) System.out.println("At t=" + state.schedule.getTime() + ", " +  getName()+ " ordering " +  neededAmount + " units of " + getTypical());
	    Resource onTheTruck = new CountableResource((CountableResource)getTypical(), neededAmount);
	    Provider provider = null;  // FIXME: replace with a bottomless Source
	    supplierDelay.accept(provider, onTheTruck, neededAmount, neededAmount);
	    batchesOrdered++;
	}

	
	//  the Queue.step() call resource offers to registered receivers
	super.step(state);
    }

    double everReceieved = 0;
    
    /** This is called by the Delay when the truck arrives */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	if (((Demo)state).verbose) System.out.println("At t=" + state.schedule.getTime() + ", " +  getName()+ " receiving "+
			   atLeast + " to " +  atMost + " units of " + amount +
					      ", while delay.ava=" + supplierDelay.getAvailable());
	double s0 = getAvailable();
	boolean z = super.accept(provider, amount, atLeast, atMost);
	everReceieved += getAvailable()-s0;
	return z;
    }
 
 
}

