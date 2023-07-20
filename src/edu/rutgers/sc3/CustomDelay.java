package  edu.rutgers.sc3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;
import edu.rutgers.sc3.Production.NeedsPriming;

/** The base class for ProdDelay, QaDelay, etc.  This class
    provides for random delay time that can be
    influenced by the batch size and/or by disruptions,
    as encapsulated in the DelayRules object.
*/
public class CustomDelay extends Delay 
	    //implements Reporting, Reporting.HasBatches, NeedsPriming
{
    /** This controls how the delay time for each batch is computed */
    protected DelayRules delayRules;
    void setDelayRules(DelayRules dt) { delayRules = dt; }

    public CustomDelay(SimState state, Resource typicalBatch) {
	super(state, typicalBatch);
    }


    /** A wrapper on super.accept() that sets the individually-computed delay time and may also do some statistics.	
     */
    public boolean accept(Provider provider, Resource r, double atLeast, double atMost) {
	double now = state.schedule.getTime();
	double amt = Batch.getContentAmount(r);
	if (delayRules==null) throw new AssertionError("delayRules not set for CustomDelay " + getName());
	double delayTime = delayRules.drawDelayTime(now, (long)amt);

	//boolean debug = getName().equals("OutputDelay.arraySmallAssembly");
	//if (debug) System.out.println("DEBUG: " + getName() + ", t="+now+", set delay=" + delayTime);
	
	setDelayTime( delayTime);
	
	boolean z = super.accept( provider, r, atLeast, atMost);
	if (!z) throw new AssertionError("Unexpected rejection of accept ny " + getName());
	return z;
    }
}
