package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;


import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;
import edu.rutgers.pharma3.Disruptions.Disruption;

/** The main Queue is the storage facility; additionally a Delay is used to ship things out */
public class Distributor extends Pool
			     //			     sim.des.Queue    implements Reporting,	Named
{

    int interval;    
      
    Distributor(SimState state, String name, Config config,
		Batch resource) throws IllegalInputException, IOException {
	
	super(state, name, config, resource, new String[0]);	
	setOffersImmediately(false); // shipping to be done only on the proper schedule

	interval = (int)para.getLong("interval");
    }

 
    private int lastMonthShippedAt= -1;
  
    private double lastOrderedAt = 0;
    private int batchesOrdered = 0;

    
    /** Makes monthly product orders from the FC */
    public void step​(sim.engine.SimState state) {

	disrupt(state);
		    
	double t = state.schedule.getTime();
	int month = (int)Math.floor(t/interval);

	double orderSize = 0;
	if (month> lastMonthShippedAt) {
	    final int MONTHS = 4;
	    final int N = interval*MONTHS;
	    double avgMonthDemand =  getRecentDemand(N)/MONTHS;
	    if (t < N) avgMonthDemand  += (initial*(N-t))/N;
	    
	    //lastOrderedAt  = t;
	    orderSize =	Math.round(avgMonthDemand);
	    everOrdered += orderSize;

	    Resource orderPaper = new CountableResource(PharmaCompany.drugOrderResource, orderSize);
	    Provider provider = null;  // FIXME: replace with a bottomless Source
	    if (orderSize==0) {
		System.out.println("Warning: At t="+t+", " + getName() + " skips its monthly order because the avg demand computes to 0!");
	    } else {
		((Demo)state).getPharmaCompany().accept(provider, orderPaper, orderSize, orderSize);
		batchesOrdered++;
	    }
	    lastMonthShippedAt = month;
	}
	// instead of super.step(), just do what's needed in this class
	fillBackOrders();
	orderedToday = orderSize;
	doChart();
    }
    

    private void disrupt(SimState state) {
	Vector<Disruption> vd = ((Demo)state).hasDisruptionToday(Disruptions.Type.Depletion,getName());
	if (vd.size()==1) {
	    // deplete inventory
	    double amt = Math.round(vd.get(0).magnitude * 1e7);
	    deplete(amt);			    
	} else if (vd.size()>1) {
	    throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	}    	
    }
    

    /*
   public String report() {
             
       String s =
	   "TotalReceivedResource=" +  getTotalReceivedResource() + " ba. " +
	   "Shipping plan=" + needsToShip +" u, has shipped=" + everShipped +
	   "u ("+(long)everShippedBatches+" ba) , in " + loadsShipped+ " loads. Of this, " +
	   (long)shipOutDelay.getDelayed() + " ba is still in transit. Remains on hand=" + getAvailable() + " ba";

       if (discardedExpiredBatches>0) s += ", discarded as expired=" + discardedExpiredBatches +  " ba";
       if (stolen>0) s += ", stolen=" + stolenBatches +  " ba";
       
       return wrap(s);
    }
    */
    
}
