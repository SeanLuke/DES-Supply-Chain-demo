 package  edu.rutgers.pharma3;

import java.util.*;
//import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
//import sim.des.portrayal.*;

import edu.rutgers.util.*;
import edu.rutgers.pharma3.Disruptions.Disruption;

/** This is the actual "production stage" within a MaterialSuppler or
    a Production unit. 

    Among other things, it can modify properties of lots it 
    offers to the receiver on certain days. This abiliy is controlled
    via the disruption schedule.

*/
public class ProdDelay extends Delay implements Reporting {
    /** Total batches started */
    int batchCnt=0;
    public int getBatchCnt() { return batchCnt; }
    /** Total units (pills) started */
    double totalStarted=0;
    public double getTotalStarted() { return totalStarted; }

    /** @param resource Whatever we produce.
     */
    ProdDelay(SimState state,  Resource resource) {
	super(state, resource);
    }
    public boolean accept(Provider provider, Resource r, double atLeast, double atMost) {
	double amt = 
	    (r instanceof Batch)?
	    ((Batch)r).getContentAmount():
	    r.getAmount();
	if (r instanceof Batch) 	batchCnt++;
	totalStarted+=amt;
	return super.accept( provider, r, atLeast, atMost);
    }
    
    public String hasBatches() {
	String s = "" + (long)getDelayed();
	if (getAvailable()>0) s += "+"+(long)getAvailable();
	return s;
    }
	       
    public String report() {
	return "[Production line ("+getTypical().getName()+"): accepted " +  batchCnt+" ba, totaling " + (long)totalStarted+"]";
    }

    
    private Timer faultRateIncrease = new Timer();
    /** This is used by a disruptor to reduce the quality of the products
	produced by this unit over a certain time interval. */
    void setFaultRateIncrease(double x, Double _untilWhen) {
	faultRateIncrease.setValueUntil(x,_untilWhen);
    }
 
    /** Sometimes reduces the quality of the offered batch */
    protected boolean offerReceiver(Receiver receiver, Entity entity) {
	double t = state.schedule.getTime();

	Batch b  = (Batch)entity;
	b.getLot().increaseInFaultRate = faultRateIncrease.getValue(t);

	return super.offerReceiver( receiver, entity);
    }    
	    
}
    
