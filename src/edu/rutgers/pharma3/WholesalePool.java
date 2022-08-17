package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models the Hospital/Pharmacies Pool. The main queue represents the 
    stored product, which has been received from Distribution and
    is available for Consumer.
 */
public class WholesalePool extends sim.des.Queue implements Reporting {


    /** From the DC */
    public double getEverReceivedClean() {
	return everReceivedClean;
    }
    
    double everReceivedClean = 0;

    /** From the Untrusted Supplier Pool */
    public double getEverReceivedDirty() {
	return everReceivedDirty;
    }
    double everReceivedDirty = 0;

    final double batchSize;

    /** Similar to typical, but with storage. In this case, it's batches of packaged drug  */
    private final Batch prototype;
 
    
    /** @param resource Batch resource for the finished product (packaged drug)
     */
    WholesalePool(SimState state, String name, Config config,
		 Batch resource) throws IllegalInputException {
	super(state, resource);	
	prototype = resource;
	//System.out.println("HP: resource = " + resource  );


	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	double initial = para.getDouble("initial");
	batchSize = para.getDouble("batch");
	initSupply(initial);
    }


    /** Loads the Queue with the "initial supply", in standard size batches made today */
    private void initSupply(double initial) {
	int n = (int)Math.round( initial / batchSize);
	double now = state.schedule.getTime();
	for(int j=0; j<n; j++) {
	    Batch whiteHole = prototype.mkNewLot(batchSize, now);
	    Provider provider = null;  // why do we need it?
	    if (accept(provider, whiteHole, 0, 0)) throw new AssertionError("Queue did not accept");       
	}	
    }

     public String report() {
	 String s = "Report not supported yet";
	   //"Has ordered="+everOrdered+"; has " + getContentAmount() + " units of " + prototype.getContent().getName() + " on hand. Has Received="+
	   //everReceived;
       return wrap(s);
   }


}
