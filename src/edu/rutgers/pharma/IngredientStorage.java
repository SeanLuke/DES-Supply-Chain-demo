package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Ingredient storage */
class IngredientStorage extends sim.des.Queue implements Reporting {

    /** It seems like one needs a Provider object to call Delay.accept()
	with,  	so let's create one. 
     */
    //Provider dummyProvider;
    /** Models the ordering of the ingredient from the infinite supply
	pool, and the delay involving in processing this order (say,
	shipping by truck) */
    Delay supplierDelay;

    final double threshold, restock;

    
    IngredientStorage(SimState state, String name, Config config,
		      CountableResource resource) throws IllegalInputException {
	super(state, resource);	
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	setCapacity(para.getDouble("capacity"));

	threshold = para.getDouble("threshold");
	restock = para.getDouble("restock");
	
	supplierDelay = new Delay(state,resource);
	supplierDelay.setDelayDistribution(para.getDistribution("supplierDelay",state.random));
	supplierDelay.addReceiver(this);
    }

    public String report() {
	return "[IngStor."+getName()+"(cap="+getCapacity()+"), has " + resource.getAmount() + " units of " + getTypicalProvided().getName() + ". EverReceived="+
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
	// FIXME: Abhisekh likes to "over-order" (asking for 800 units),
	// but I am not doing it because I don't have proper support for
	// a possible overflow. 
	if (nothingInSupplyQueue() &&
	    resource.getAmount() < threshold) {
	    double neededAmount = Math.min(restock, getCapacity() - resource.getAmount());
	    if (Demo.verbose) System.out.println("At t=" + state.schedule.getTime() + ", " +  getName()+ " ordering " +  neededAmount + " units of " + getTypicalProvided());
	    Resource onTheTruck = new CountableResource((CountableResource)getTypicalProvided(), neededAmount);
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

