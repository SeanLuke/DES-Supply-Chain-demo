package  edu.rutgers.sc2;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** The Reordering facility can be associated with a Queue object in order to
    enable it to order stuff from an external supplier. */
class Reordering {
    protected double reorderPoint=0;
    private double targetLevel=0;

  /** How much this pool has ordered from its own suppliers, for its own
	replenishment */
    protected double everOrdered=0;
    
    final MaterialBuffer whose;

    Delay supplierDelay;


    private Reordering(MaterialBuffer _whose, 
		       SimState state,
		       ParaSet para) throws IllegalInputException, IOException {

	whose = _whose;
	Double r = para.getDouble("reorderPoint", null);

	reorderPoint = r;

	r =  para.getDouble("targetLevel", null);
	if (r==null)  throw new  IllegalInputException("Element named '" + whose.getName() +"' has reorder point, but no target level");
	targetLevel = r;

	supplierDelay = new Delay(state, whose.prototype);
	supplierDelay.setDelayDistribution(para.getDistribution("supplierDelay",state.random));
	supplierDelay.addReceiver(whose);

	
    }

    static Reordering mkReordering(MaterialBuffer whose, 
	       SimState state,
	       ParaSet para) throws IllegalInputException, IOException {

	Double r = para.getDouble("reorderPoint", null);
	if (r==null) return null;

	return new Reordering(whose, state, para);


    }

    /** The outstanding order amount: the stuff that this pool has ordered, but which has not arrived yet.  It is used so that the pool does not try to repeat its order daily until the orignal order arrives.
      FIXME: it would be better to have separate vars for separate suppliers
 */
    protected double onOrder = 0;


    
    /** Reported in a time series chart file */
    double orderedToday = 0;

    /** Checks if this pool needs stuff reordered, and makes an order if needed */
    void reorderCheck() {
    	double t = whose.getState().schedule.getTime();

	double have =  whose.getContentAmount() + onOrder;
	if (have > reorderPoint) return;
	double needed = Math.round(targetLevel - reorderPoint);
	double unfilled = needed;


	if (unfilled>0) {
	    CountableResource shipment = new CountableResource((CountableResource)whose.prototype, unfilled);
	    Provider p = null;
	    if (!supplierDelay.accept(p, shipment, unfilled, unfilled)) throw new IllegalArgumentException("supplierDelay failed to accept");
	    double sent =unfilled;
	    unfilled -= sent;
	    onOrder += sent;
	}

	orderedToday = needed;
	everOrdered += orderedToday;

    }

    /** Checks if the delivery has come from our built-in supplier,
	and if it has, adjusts the "onOrder" amount. This is called
	from InputBuffer.accept()
	 
     */
    void onAccept(Provider provider, double a)  {
	if (provider == supplierDelay) { // Received a non-immediate delivery
	    onOrder -= a;
	    if (onOrder < 0) { // they have over-delivered
		onOrder=0;
	    }
	}
    }

    /** This is called from InputBuffer.chart(), after the data have been written out.
	It then resets variables that are supposed to be accumulated over the one day
	charting-to-charting period
     */
    void onChart() { 	orderedToday=0; }


}
