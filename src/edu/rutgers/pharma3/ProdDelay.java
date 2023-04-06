package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;

/** The actual "production stage" within a MaterialSuppler or
    a Production unit. 

    Among other things, it can modify properties of lots it 
    offers to the receiver on certain days. This ability is controlled
    via the disruption schedule.

*/
public class ProdDelay extends SimpleDelay 
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

    /** A wrapper on super.accept() that also does some statistics.	
     */
    public boolean accept(Provider provider, Resource r, double atLeast, double atMost) {
	double amt = Batch.getContentAmount(r);
	batchCnt++;
	totalStarted+=amt;

	totalUsedTime += getDelayTime();
	
	double t = state.schedule.getTime();

	boolean z = super.accept( provider, r, atLeast, atMost);

	if (!Demo.quiet) {
	    if (r instanceof Batch) {
		((Batch)r).addToMsg("[ProdDelay.acc@"+t+", hb="+hasBatches()+"]");
	    }
	}
	
	return z;
    }
    
    public String hasBatches() {
	String s = "" + (long)getDelayed();
	if (getAvailable()>0) s += "+"+(long)getAvailable();
	return s;
    }

    static private DecimalFormat df = new DecimalFormat("0.00#");    
	       
    public String report() {
	double t = state.schedule.getTime();
	double util = (t==0)? 1.0 : totalUsedTime/t;
	return "[Production line ("+getTypicalProvided().getName()+"): accepted " +  batchCnt+" ba, totaling " + (long)totalStarted+"; utilization="+df.format(util*100)+"%]";
    }

    
    private Timed faultRateIncrease = new Timed();
    /** This is used by a disruptor to reduce the quality of the products
	produced by this unit over a certain time interval. */
    void setFaultRateIncrease(double x, Double _untilWhen) {
	faultRateIncrease.setValueUntil(x,_untilWhen);
    }
 
    /** Overrides the super.offerReceiver(Receiver,Entity) in order to sometimes
	reduce the quality of the offered batch.
	
	FIXME: this will only work for RM and Excipient, and not for
	PacMat, because PacMat is fungible and does not use this
	method. Fortunately, Abhisekh's menu of disruptions does not
	include one that affects PacMat in this way...
     */
    protected boolean offerReceiver(Receiver receiver, Entity entity) {
	double t = state.schedule.getTime();
	
	Batch b  = (Batch)entity;
	b.getLot().setIncreaseInFaultRate( faultRateIncrease.getValue(t));
	boolean z=super.offerReceiver( receiver, entity);

	return z; 
    }    
	    
}
    
