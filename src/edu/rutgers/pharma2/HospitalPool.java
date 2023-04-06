package  edu.rutgers.pharma2;

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
   
    HospitalPool(SimState state, String name, Config config,
		 CountableResource resource) throws IllegalInputException {
	super(state, resource);	
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	intervalBetweenOrders= para.getDouble("intervalBetweenOrders");
	orderSize = para.getDouble("orderSize");
	//setCapacity(para.getDouble("capacity"));

	//threshold = para.getDouble("threshold");
	//restock = para.getDouble("restock");
	
	//supplierDelay = new Delay(state,resource);
	//supplierDelay.setDelayDistribution(para.getDistribution("supplierDelay",state.random));
	//supplierDelay.addReceiver(this);
    }

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
	    everOrdered += orderSize;

	    Resource orderPaper = new CountableResource((CountableResource)pharmaCompany.getTypicalReceived(), orderSize);
	    Provider provider = null;  // FIXME: replace with a bottomless Source
	    
	    pharmaCompany.accept(provider, orderPaper, orderSize, orderSize);
	    batchesOrdered++;
	}
    }
    
    /** This is called by the Delay when the truck arrives */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	if (((Demo)state).verbose) System.out.println("At t=" + state.schedule.getTime() + ", " +  getName()+ " receiving "+
						      atLeast + " to " +  atMost + " units of " + amount );
	//+					      ", while delay.ava=" + supplierDelay.getAvailable());
	double s0 = getAvailable();
	boolean z = super.accept(provider, amount, atLeast, atMost);
	everReceived += getAvailable()-s0;
	return z;
    }
 
    
   public String report() {
       String s = "Has ordered="+everOrdered+"; has " + resource.getAmount() + " units of " + getTypicalProvided().getName() + " on hand. Has Received="+
	   everReceived;
       return wrap(s);
   }


    
}
