package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models the End Consumer Pool. It requests and receives Batches of
    packaged product on a daily schedule from a specified upstream
    pool.

    It is implemented as a Sink, because once a batch of product has arrived
    here, nothing much needs to be done with it anymore, other than counting it.

 */
public class EndConsumer extends MSink implements Reporting {

    private final AbstractDistribution dailyDemandDistribution;

    
    /** Similar to typical, but with storage. In this case, it's batches of packaged drug  */
    private final Batch prototype;
  
    /** 
	@param resource The batch resource consumed here
     */
    public EndConsumer(SimState state, String name, Config config,
		 Batch resource) throws IllegalInputException {
       
	super(state,resource);
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new IllegalInputException("Config file has no data for unti=" + name);
	prototype = resource;
	dailyDemandDistribution = para.getDistribution("dailyDemand",state.random);
    }


    /** Specifies from where we take stuff to satisfy the demand */
    void setSource(Pool _source) {
	source = _source;
    }

    /** The pool from where  we take stuff to satisfy the demand */
    private Pool source;

    /** The amount that has been over-ordered previously, due to up-rounding.
	It will be carried forward and deducted from the next day's demand.
     */
    private double hasExtra = 0;

    private double  totalUnsatisfiedDemand = 0;

    /** How many bad pills have been included in the total receipt.
	(Bad pills may have come here if one of the upstream sources
	got them mixed into a load received from the Untrusted Supplier).
     */
    private double everReceivedBad =0;
    
    /** Consumes product out of the Hospital/Pharmacy pool on a certain schedule */
    public void step​(sim.engine.SimState state) {
	double demand = dailyDemandDistribution.nextDouble();
	if (demand<0) throw new AssertionError("Negative demand");
	
	double reduceBy = Math.min(hasExtra,demand);
	demand -= reduceBy;
	hasExtra -= reduceBy;
	if (demand==0) return;

	double sent = source.feedTo(this, demand);
	if (sent > demand) {
	    hasExtra += (sent - demand);
	} else {
	    totalUnsatisfiedDemand  += (demand - sent);
	}
  	
    }

    /** In addition to the normal acceptance, counts bad pills mixed into 
	the recieved lot, for stats. */
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	if (!(resource instanceof Batch)) throw new IllegalArgumentException("EndConsumer expects to only received Batches");
	everReceivedBad += ((Batch)resource).getLot().illicitCount;
	return  super.accept( provider,  resource, atLeast,  atMost);
    }

    
    public String report() {
	return super.report() + "\n" +
	    "Received bad units=" + 	everReceivedBad + ". " +
	    "Total unfulfilled demand=" + totalUnsatisfiedDemand + " u";
    }
	   

 
    
}
