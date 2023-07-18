package  edu.rutgers.sc3;

import java.util.*;

//import sim.engine.*;
//import sim.util.*;
import sim.util.distribution.*;
//import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;
//import edu.rutgers.supply.Disruptions.Disruption;
//import edu.rutgers.sc3.Production.NeedsPriming;

/** This is an auxiliary class used to compute the time that a
    particular batch will take to go through a production or QA
    stage. The assumptions are that the time needed to process the
    batch is not just drawn from a certain fixed distribution,
    but may also be affected by the size of the batch and by
    external (time-based) factors (disruptions). Therefore, the
    Delay or SimpleDelay controlled by this DelayRules will
    compute and set the delay time individually for each batch,
    inside its delay() method before calling super.delay().

    <p>The computed delay time for a batch is based on the "basic"
    distribution for a batch processing time (or the "basic"
    distribution for the unit processing time and the number of units
    in this batch), but may be affected by disruptions that slow down
    processing.

    <p>A typical usage, inside the accept() method of a Delay or SimpleDelay
    object:
    <pre>
    	double now = state.schedule.getTime();
	double amt = Batch.getContentAmount(resource);
	double delayTime = delayRules.drawDelayTime(now, (long)amt);
	setDelayTime( delayTime);
	super.accept0....);
	</pre>

*/

class DelayRules {

    /** For a fixed-time delay */
    DelayRules(double fixed) {
	fixedT = fixed;
	unit = false;
	myDelayDistribution = null;
	delayFactor = null;
    }
    
    
    DelayRules(AbstractDistribution _delayDistribution, boolean _unit, Timed _delayFactor) {
	fixedT = null;
    	myDelayDistribution = _delayDistribution;
	unit = _unit;
	delayFactor = _delayFactor;
    }

    /** The simple alternative, for a fixed-time delay */
    private final Double fixedT;
    
    
    /** This is interpreted as either the distribution from which the time of 
	 processing a batch is drawn (if unit==false), or the time of processing
	 a single unit is drawn (if unit==true). In the latter case, n values
	 are drawn from this distribution to obtain a time for processing a batch
	 of n units.
    */
    private final AbstractDistribution myDelayDistribution;
    /** The disruption-controlled factor that may make all delays longer */
    private final Timed delayFactor;

    /** If true, the delayDistribution contains unit cost, rather than batch cost */
    private final boolean unit;

    /** Without disruptions */
    private double basicComputeDelayTime(long n) {
	double sum=0;
	if (!unit) n = 1;
	for(int j=0; j<n; j++) {
	    sum += myDelayDistribution.nextDouble();
	}
	return Math.abs(sum);
    }

    double drawDelayTime(double now, long amt) {
	if (fixedT!=null) return fixedT;
	
	double delayTime = basicComputeDelayTime( amt );

	if (delayFactor != null && delayFactor.isOn(now)) {
	    double f = delayFactor.getValue(now);
	    delayTime *= f;
	    //System.out.println("DEBUG: factor=" + f +", gives delayTime=" + delayTime);
	}
	return delayTime;
    }

    
}

    




  
