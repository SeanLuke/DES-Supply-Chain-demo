package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

/** A Metered Sink is a wrapper around Sink, designed to print some
    debugging information and to keep track of the amount of resource
    it has consumed.
 */
class MSink extends Sink {
    /** How much stuff has this Sink consumed since it's been created? */
    double everConsumed = 0;
	
    public MSink(SimState state, Resource typical) {
	super(state,typical);
    }
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	String name = "?";
	if (provider instanceof Named) {
	    name = ((Named)provider).getName();
	}
	CountableResource cr = (CountableResource) resource;
	double amt0 = cr.getAmount();
	
	boolean z =super.accept( provider,  resource,  atLeast,  atMost);
	double amt1 = cr.getAmount();
	everConsumed += amt0 - amt1;
	//System.out.println("Sink.accept(" + name + ","+amt0+"/"+amt1+","+ atLeast+","+ atMost +")");
	return z; 
    }
 
  
}
    
