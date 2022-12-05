package  edu.rutgers.pharma3;

import java.util.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;

/** This is the actual "production stage" within a MaterialSuppler or
    a Production unit. 

    Among other things, it can modify properties of lots it 
    offers to the receiver on certain days. This abiliy is controlled
    via the disruption schedule.

*/
public class ProdDelay extends SimpleDelay // Delay
    implements Reporting, Reporting.HasBatches {
    /** Total batches started */
    int batchCnt=0;
    public int getBatchCnt() { return batchCnt; }
    /** Total units (pills) started */
    double totalStarted=0;
    public double getTotalStarted() { return totalStarted; }

    /** Statistics used for reporting utilization rate */
    double totalUsedTime = 0;

    
    /** @param resource Whatever we produce.
     */
    ProdDelay(SimState state,  Resource resource) {
	super(state, resource);
	setName("ProdDelay of " + resource.getName());
    }

        
    public boolean accept(Provider provider, Resource r, double atLeast, double atMost) {
	double amt = Batch.getContentAmount(r);
	batchCnt++;
	totalStarted+=amt;

	totalUsedTime += getDelayTime();

	
	double t = state.schedule.getTime();

	//System.out.println("At " + t +", "+getName() + " accepting " + r+", had=" + hasBatches());

	boolean z = super.accept( provider, r, atLeast, atMost);

	if (!Demo.quiet) {
	    if (r instanceof Batch) {
		((Batch)r).addToMsg("[ProdDelay.acc@"+t+", hb="+hasBatches()+"]");
	    }
	}

	
	if (Demo.verbose && getName().indexOf("PackagingMat")>=0) 
	    System.out.println("At " + t +", "+getName() + " accepted " + r+"? Result=" + z +"; has=" + hasBatches());
	return z;
    }
    
    public String hasBatches() {
	String s = "" + (long)getDelayed();
	if (getAvailable()>0) s += "+"+(long)getAvailable();
	return s;
    }
	       
    public String report() {
	double t = state.schedule.getTime();
	double util = (t==0)? 1.0 : totalUsedTime/t;
	return "[Production line ("+getTypical().getName()+"): accepted " +  batchCnt+" ba, totaling " + (long)totalStarted+"; utilization="+util+"]";
    }

    
    private Timed faultRateIncrease = new Timed();
    /** This is used by a disruptor to reduce the quality of the products
	produced by this unit over a certain time interval. */
    void setFaultRateIncrease(double x, Double _untilWhen) {
	faultRateIncrease.setValueUntil(x,_untilWhen);
    }
 
    /** Sometimes reduces the quality of the offered batch.
	FIXME: this will only work for RM and Excp, not for PacMat, because
	PacMat is fungible and does not use this method.
     */
    protected boolean offerReceiver(Receiver receiver, Entity entity) {
	double t = state.schedule.getTime();


	
	Batch b  = (Batch)entity;
	b.getLot().setIncreaseInFaultRate( faultRateIncrease.getValue(t));
	boolean z=super.offerReceiver( receiver, entity);

	//System.out.println("At " + t +", "+getName() + " offering to " + receiver.getName()+", result=" + z +"; receiver.ava=" + getAvailable());
	

	return z; 
    }    
	    
}
    
