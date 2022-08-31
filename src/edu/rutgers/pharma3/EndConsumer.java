package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models the End Consumer Pool. It receives Batches of packaged product
 */
public class EndConsumer extends MSink implements Reporting {


    final AbstractDistribution dailyDemandDistribution;

    
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
    private Pool source;

    // leftover from up-rounding
    double hasExtra = 0;

    double  totalUnsatisfiedDemand = 0;
    
    /** Consumes product out of the Hospital/Pharmacy pool on a certain schedule */
    public void step​(sim.engine.SimState state) {
	double demand = dailyDemandDistribution.nextDouble();
	demand -= hasExtra;
	hasExtra = 0;
	// round to whole batches
	//int nb = (int)Math.round( demand / 
	//demand = 
	double sent = source.feedTo(this, demand);
	if (sent > demand) {
	    hasExtra = sent - demand;
	} else {
	    totalUnsatisfiedDemand  += demand - sent;
	}
    }

    public String report() {
	return super.report() + "\n" +
	    "Total unfulfilled demand=" + totalUnsatisfiedDemand;
    }
	   

 
    
}
