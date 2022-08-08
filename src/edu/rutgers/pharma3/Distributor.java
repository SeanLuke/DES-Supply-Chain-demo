package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;


import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** The main Queue is the storage facility; additionally a Delay is used to ship things out */
public class Distributor extends sim.des.Queue
    implements Reporting,	Named
{

    int interval;
    double batchSize;
    
    Delay shipOutDelay;


    Sink expiredProductSink;
    
    
    private Charter charter;
    
    Distributor(SimState state, String name, Config config,
		Batch resource) throws IllegalInputException, IOException {
	super(state, resource);	
	setName(name);
	setOffersImmediately(false); // shipping to be done only on the proper schedule
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	batchSize = para.getDouble("batch");
	interval = (int)para.getLong("interval");

	shipOutDelay = new Delay( state,  resource);
	shipOutDelay.setDelayDistribution(para.getDistribution("shipOutDelay",state.random));				       

	expiredProductSink = new Sink(state,  resource);
	charter=new Charter(state.schedule, this);
    }

    //Receiver rcv;

    /** In units */
    private double needsToShip=0, everShipped=0, everShippedBatches=0;
    public double getNeedsToShip() { return needsToShip; }
    public double getEverShipped() { return everShipped; }

    /** The amount of product that has been discarded because we discovered
	that it was too close to expiration */
    private double discardedExpired = 0;
    private double discardedExpiredBatches = 0;

    
    /** To whom are we shipping */
    void setDeliveryReceiver(Receiver rcv) {
	shipOutDelay.addReceiver(rcv);
    }

    /** Adds x to the total amount of stuff that we need to ship eventually.
	@param x In units
     */
    void addToPlan(double x)
    {
	needsToShip += x;	
    }
    

    private double lastShippedAt = 0, lastMonthShippedAt=0;
    private int loadsShipped = 0;
    public int getLoadsShipped() { return loadsShipped; }

    /** What was the total amount of drug that the recievers
	have accepted from us during this shipment day? */
    private double sumLastOfferContentAmounts() {
	ArrayList<Resource> lao = getLastAcceptedOffers();
	if (lao==null) throw new IllegalArgumentException("Unexpected result from getLastAcceptedOffers(). lao=null");
	double sum = 0;
	for(Resource r: lao) {
	    Batch b = (Batch)r;
	    sum += b.getContentAmount();
	}
	return sum;	
    }

 

    /** Ships product out on a certain schedule */
    public void step​(sim.engine.SimState state) {
	double shippedToday = 0;
	    
	double t = state.schedule.getTime();
	double month = Math.floor(t/interval);

	//-- How many batches can we form?
	double stillNeeded =  needsToShip - everShipped;
	
	if ( (month> lastMonthShippedAt) && stillNeeded>0) {

	    while(getAvailable()>0 && shippedToday<stillNeeded) {
		
		Batch b = (Batch)entities.getFirst();

		//System.out.println("willExpire="+b.willExpireSoon(t, 365)+"; gA=" + getAvailable());
		
		if (b.willExpireSoon(t, 365)) {
		    //System.out.println("expired batch; gA=" + getAvailable());
		    if (!offerReceiver( expiredProductSink, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		    discardedExpired += b.getContentAmount();
		    discardedExpiredBatches ++;
		    //System.out.println("discarded="+discardedExpired+"; gA=" + getAvailable());
		    entities.remove(b);
		    continue;
		}

		
		if (!offerReceiver( shipOutDelay, b)) break;		
		shippedToday += b.getContentAmount();
		everShippedBatches ++;
		// FIXME: maybe wont be needed later on (Qu. 30)
		entities.remove(b);
	    }

	    stillNeeded -= shippedToday;
	    
	    if (Demo.verbose) System.out.println("At t=" + state.schedule.getTime() + ", Distro has " +  getAvailable() +" batches, stillNeeded="+stillNeeded +
						 ". Shipped today=" + shippedToday);

	    if (shippedToday>0) {
		everShipped += shippedToday;
		lastShippedAt = t;
		lastMonthShippedAt=month;
		loadsShipped++;
	    }
	}
	charter.print(shippedToday);
    }

    
   public String report() {
             
       String s =
	   "TotalReceivedResource=" +  getTotalReceivedResource() + " ba. " +
	   "Shipping plan=" + needsToShip +" u, has shipped=" + everShipped +
	   "u ("+(long)everShippedBatches+" ba) , in " + loadsShipped+ " loads. Of this, " +
	   (long)shipOutDelay.getDelayed() + " ba is still in transit. Remains on hand=" + getAvailable() + " ba";

       if (discardedExpiredBatches>0) s += ", discarded as expired=" + discardedExpiredBatches +  " ba";
       
       return wrap(s);
    }

    
}
