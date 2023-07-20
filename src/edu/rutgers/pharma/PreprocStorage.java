package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** A Preprocessing Storage unit receievs an ingredient from
    an Ingredient Storage unit (via a built-in quality check unit),
    and supplies quality-assured ingredient to the Production unit.
  */
class PreprocStorage extends sim.des.Queue implements Reporting {

  /** Enables access to the resource, so that we can pass it as an argument
	in the accept() calls in downstream users 
    */
    //CountableResource getResource() { return resource; }
  

    
    /** (1,4) */
    AbstractDistribution qaDelayDistribution;

    /** Take stuff from there */
    IngredientStorage ingStore;
    /** Models the delay taking by the QA testing at the input to the 
	preproc storage
     */
    QaDelay qaDelay;
    final double batchSize;

    
    PreprocStorage(SimState _state, String name, Config config,
		   IngredientStorage _ingStore) throws IllegalInputException {
    
	super(_state, _ingStore.getTypicalReceived() );

	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	setCapacity(para.getDouble("capacity"));

	batchSize = para.getDouble("batch");
	
	ingStore = _ingStore;

	qaDelay = new QaDelay(getState(),resource, para.getDistribution("faulty",getState().random));
	qaDelay.setDelayDistribution(para.getDistribution("qaDelay",getState().random));
	qaDelay.addReceiver(this);
    }

    public String report() {
	return "[PrePStor."+getName()+"(cap="+getCapacity()+"), has " + resource.getAmount() + " units of " + getTypicalProvided().getName() + ", plus "+qaDelay.getDelayed()+"+"+qaDelay.getAvailable()+" still in QA; discarded "+qaDelay.badResource+" faulty units, accepted "+qaDelay.releasedGoodResource+" good ones]";
    }


    int batchesOrdered=0;
    
    /** If our storage is not full (taking into account the batches
	that are currently been checked), get a batch into checking
     */
    public void step​(sim.engine.SimState _state) {
	boolean hasSpace = resource.getAmount() + qaDelay.getDelayed() <= getCapacity() - batchSize;
	if (hasSpace &&    ingStore.getAvailable() >=  batchSize	    ) {
	    double neededAmount = batchSize;
	    //System.out.println("At t=" + getState().schedule.getTime() + ", " +  getName()+ " requesting QA on " +  neededAmount + " units of " + getTypicalProvided());
	    //Resource onTheTruck = new CountableResource((CountableResource)getTypicalProvided(), neededAmount);
	    //	    qaDelay.accept(ingStore, ingStore.getResource(), neededAmount, neededAmount);
	    ingStore.provide( qaDelay, neededAmount);
	    
	    batchesOrdered++;
	} else if (!hasSpace) {
	    //   System.out.println("At t=" + getState().schedule.getTime() + ", " +  getName()+ " is full already"); 
	} else {
	    //	    System.out.println("At t=" + getState().schedule.getTime() + ", " +  getName()+ " cannot order (no supply available)"); 
	}

	
	//  the Queue.step() call resource offers to registered receivers
	super.step(getState());
    }

    /** Overriding accept() in order to print statistics */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	double s0=getAvailable();

	String msg = "At t=" + getState().schedule.getTime() + ", " +  getName()+ " receiving "+
	    atLeast + " to " +  atMost + " units of " + amount +
	    ", while qa.ava=" + qaDelay.getAvailable();

	
	boolean z = super.accept(provider, amount, atLeast, atMost);
	double s=getAvailable();
	msg += "; stock changed from " + s0 + " to " +s;	
	//qSystem.out.println(msg);

	return z;

    }
 
 
}

