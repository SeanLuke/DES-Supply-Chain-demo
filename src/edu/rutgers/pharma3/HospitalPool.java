package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models the Hospital/Pharmacies Pool. The main queue represents the 
    stored product, which has been received from Distribution and
    is available for Consumer.
 */
public class HospitalPool extends sim.des.Queue implements Reporting {


    double everReceived = 0;
    
    public double getEverReceived() {
	return everReceived;
    }
    public double getEverOrdered() {
	return everOrdered;
    }
    
    /** Interval between orders, e.g. 30 days */
    final double intervalBetweenOrders;
    double orderSize;// = 1.9e6;

    /** Similar to typical, but with storage. In this case, it's batches of packaged drug  */
    private final Batch prototype;
    
    
    /** @param resource Batch resource for the finished product (packaged drug)
     */
    HospitalPool(SimState state, String name, Config config,
		 Batch resource) throws IllegalInputException {
	super(state, resource);	
	prototype = resource;
	//System.out.println("HP: resource = " + resource  );


	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	intervalBetweenOrders= para.getDouble("intervalBetweenOrders");
	//orderSize = para.getDouble("orderSize");


	monthlyOrderDistribution = para.getDistribution("order",state.random);

	expiredProductSink = new ExpiredSink(state,  resource, 365);
       
	
	//setCapacity(para.getDouble("capacity"));

	//threshold = para.getDouble("threshold");
	//restock = para.getDouble("restock");
	
	//supplierDelay = new Delay(state,resource);
	//supplierDelay.setDelayDistribution(para.getDistribution("supplierDelay",state.random));
	//supplierDelay.addReceiver(this);
    }

    final AbstractDistribution monthlyOrderDistribution;
    private PharmaCompany pharmaCompany;

    /** Sets the destinaton to which orders are set. Call this soon
	after the object has been created */
    void setOrderDestination(PharmaCompany _pharmaCompany) {
	pharmaCompany = _pharmaCompany;
    }


    private double everOrdered=0, lastOrderedAt = 0;
    private int batchesOrdered = 0;

    /** Every now and then, send an order to the PharmaCompany */
    public void step​(sim.engine.SimState state) {

	double t = state.schedule.getTime();

	if (everOrdered==0 || t- lastOrderedAt >= intervalBetweenOrders) {
	    lastOrderedAt  = t;
	    double orderSize =	monthlyOrderDistribution.nextDouble();
	    orderSize = Math.round(orderSize);
	    everOrdered += orderSize;

	    Resource orderPaper = new CountableResource((CountableResource)pharmaCompany.getTypical(), orderSize);
	    Provider provider = null;  // FIXME: replace with a bottomless Source
	    
	    pharmaCompany.accept(provider, orderPaper, orderSize, orderSize);
	    batchesOrdered++;
	}
    }
    
    /** This is called from the Distribution when it sends products over
	@param amount a Batch object
     */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	if (((Demo)state).verbose) System.out.println("At t=" + state.schedule.getTime() + ", " +  getName()+ " receiving "+
						      atLeast + " to " +  atMost + " units of " + amount );
	//+					      ", while delay.ava=" + supplierDelay.getAvailable());
	//double s0 = getAvailable();
	double a = ((Batch)amount).getContentAmount();
	boolean z = super.accept(provider, amount, atLeast, atMost);
	if (z) {
	    everReceived += a; // getAvailable()-s0;
	}
	return z;
    }
 
    
   public String report() {
       String s = "Has ordered="+everOrdered+"; has " + getContentAmount() + " units of " + prototype.getContent().getName() + " on hand. Has Received="+
	   everReceived;
       return wrap(s);
   }

    public double getContentAmount()        {
        if (resource != null) {
            return resource.getAmount();
	} else if (entities != null) {
	    double sum = 0;
	    for(Entity e: entities) {
		sum +=  (e instanceof Batch)? ((Batch)e).getContentAmount() : e.getAmount();
	    }
            return sum;
	}  else {
            return 0;
        }
    }

    /** Keeps track of the amount of product that has been discarded because we discovered
	that it was too close to expiration.  */
     ExpiredSink expiredProductSink;
  

    /** Handles the request from a receiver (such as the EndConsumer) to send to it 
	a number of batches, totaling at least a specified amount of stuff.
	@param amt the requested amount
	@return the actually sent amount. (Can be a bit more than amt due to batch size rounding,
	or can be less due to the shortage of product).
     */
    double feedTo(Receiver r, double amt) {
	double sent = 0;
    	//double t = state.schedule.getTime();

	Batch b;
	while(getAvailable()>0 && sent<amt &&
	      (b = expiredProductSink.getNonExpiredBatch(this, entities))!=null) {
	    if (!offerReceiver(r, b)) throw new IllegalArgumentException("Expected acceptance by " + r);
	    sent += b.getContentAmount();
	}

	
	return sent;
    }

    
}
