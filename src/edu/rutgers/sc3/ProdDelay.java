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

/** The actual "production stage" within a MaterialSuppler or
    a Production unit. 

    Among other things, it can modify properties of lots it 
    offers to the receiver on certain days. This ability is controlled
    via the disruption schedule.

*/
public class ProdDelay extends SimpleDelay 
    implements Reporting, Reporting.HasBatches, NeedsPriming {
    /** Total batches started */
    int batchCnt=0;
    public int getBatchCnt() { return batchCnt; }
    /** Total units (pills) started */
    double totalStarted=0;
    public double getTotalStarted() { return totalStarted; }

    /** Statistics used for reporting utilization rate */
    double totalUsedTime = 0;

    /** @param resource Whatever we produce. In SC3, this must be an Entity (i.e. Batch) because we only override offerReceiver(...Entity...) and want that specific method to be triggered, and not the more general (...double...) one.
     */
    ProdDelay(SimState state,  Production whose, Resource resource, String suff) {
	super(state, resource);
	setName(whose.getName() + ".prodDelay" + suff);
	// 2023-03-31: try to prevent disappearance
	setDropsResourcesBeforeUpdate(false);
	if (!(resource instanceof Entity)) throw new IllegalArgumentException();
    }

    /** A wrapper on super.accept() that also does some statistics.	
     */
    public boolean accept(Provider provider, Resource r, double atLeast, double atMost) {
	double now = state.schedule.getTime();
	double amt = Batch.getContentAmount(r);
	batchCnt++;
	totalStarted+=amt;

	totalUsedTime += getDelayTime();
	
	double t = state.schedule.getTime();

	boolean z = super.accept( provider, r, atLeast, atMost);
	if (!z) throw new AssertionError("Unexpected rejection of accept ny " + getName());
	if (!Demo.quiet) {
	    if (r instanceof Batch) {
		((Batch)r).addToMsg("[ProdDelay.acc@"+t+", hb="+hasBatches()+"]");
	    }
	    System.out.println("DEBUG: at "+ now +", "+ getName() + " accepted a batch; now has " + hasBatches() +"; everReleased=" + getEverReleased());
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
	return "["+getName()+": accepted " +  batchCnt+" ba, totaling " + (long)totalStarted+"; released "+getEverReleased()+" ; utilization="+df.format(util*100)+"%]";
    }

       
    private Timed faultRateIncrease = new Timed();
    /** This is used by a disruptor to reduce the quality of the products
	produced by this unit over a certain time interval. */
    public void setFaultRateIncrease(double x, Double _untilWhen) {
	faultRateIncrease.setValueUntil(x,_untilWhen);
    }

    
    private double everReleased = 0;
    private double ot0 = -2;

 

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
	double r = faultRateIncrease.getValue(t);
	//	if (r!=0) System.out.println("DEBUG: at " +t +", "+ getName() + ".offerReceiver creates a batch with dr=" + r);

	double a = b.getContentAmount();
	
	b.getLot().setIncreaseInFaultRate( r );
	boolean z=super.offerReceiver( receiver, entity);
	double ot = getLastOfferTime();
	if (z) {
	    everReleased += a;
	    
	    //if (ot > ot0)   everReleased += Batch.getContentAmount(  getLastAcceptedOffers());
	    ot0 = ot;
	}

	System.out.println("DEBUG: at " +t +", "+ getName() + ".offerReceiver offers a batch, size=" +a +"; z="+z+", everReleased=" + everReleased);
	return z; 
    }

    /*
    protected boolean offerReceivers() {
	double t = state.schedule.getTime();
	boolean z = super.offerReceivers();
	System.out.println("DEBUG: at " +t +", "+ getName() + ".offerReceivers(), z="+z+", everReleased=" + everReleased +
			   ", receivers are: " + Util.joinNonBlank("| ",  getReceivers())
			   );
	return z;
    }
        
    protected boolean offerReceiver(Receiver receiver, double amount) {
	double t = state.schedule.getTime();
	boolean z = super.offerReceiver(receiver, amount);
	System.out.println("DEBUG: at " +t +", "+ getName() + ".offerReceiver("+receiver.getName()+"), z="+z+", everReleased=" + everReleased);
	return z;
    }
    */

    
    public double getEverReleased()  { return everReleased; }

    //  protected boolean offerReceivers() { //ArrayList<Receiver> receivers) {
    /** Just for debugging */
    /*
    public void step(SimState state) {
  	double now = state.schedule.getTime();
	boolean doDebug = getName().indexOf("BatchOfRMEE")>=0;
	if (doDebug) {
	    System.out.println("At t=" + now + ", " + getName() + ".step.in");
	    System.out.println("DEBUG: this prodDelay=" + report0());
	    System.out.println("DEBUG: receiver=" + ((SimpleDelay)(getReceivers().get(0))).report0());
	}
	
	super.update();

	if (doDebug) {
	    System.out.println("At t=" + now + ", " + getName() + ".update done");
	    System.out.println("DEBUG: this prodDelay=" + report0());
	    System.out.println("DEBUG: receiver=" + ((SimpleDelay)(getReceivers().get(0))).report0());
	}


	super.offerReceivers();
  

	//super.step(state);
    //boolean z = super.offerReceivers();

	if (doDebug) {
	    System.out.println("At t=" + now + ", " + getName() + ".step.out");
	    System.out.println("DEBUG: this prodDelay=" + report0());
	    System.out.println("DEBUG: receiver=" + ((SimpleDelay)(getReceivers().get(0))).report0());
	}


	//return z;
    }
    */    
}
