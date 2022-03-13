package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** The main Queue is the storage facility; additionally a Delay is used to ship things out */
public class Distributor extends sim.des.Queue
    implements Reporting //,	       Steppable, Named
{

    int interval;
    double batchSize;
    
    Delay shipOutDelay;
    
    Distributor(SimState state, String name, Config config,
		Batch resource) throws IllegalInputException {
	super(state, resource);	
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	batchSize = para.getDouble("batch");
	interval = (int)para.getLong("interval");

	shipOutDelay = new Delay( state,  resource);
	shipOutDelay.setDelayDistribution(para.getDistribution("shipOutDelay",state.random));				       

    }

    //Receiver rcv;

    double needsToShip=0, everShipped=0;
    public double getNeedsToShip() { return needsToShip; }
    public double getEverShipped() { return everShipped; }

    /** To whom are we shipping */
    void setDeliveryReceiver(Receiver rcv) {
	shipOutDelay.addReceiver(rcv);
    }

    /** Adds x to the total amount of stuff that we need to ship eventually */
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

	double t = state.schedule.getTime();
	double month = Math.floor(t/interval);

	//-- How many batches can we form?
	double stillNeeded =  needsToShip - everShipped;
	
	if ( (month> lastMonthShippedAt) && stillNeeded>0) {

	    double shippedToday = 0;
	    while(getAvailable()>0 && shippedToday<stillNeeded && offerReceiver( shipOutDelay, 1)) {
		shippedToday =  sumLastOfferContentAmounts();		
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
    }

   public String report() {
       String s = "Shipping plan=" + needsToShip +", has shipped=" + everShipped + ", in " + loadsShipped+ " loads. Of this, " + shipOutDelay.getTotal() + " is still being shipped. Remains on hand=" + getAvailable() + " batches";
       return wrap(s);
    }

    
}
