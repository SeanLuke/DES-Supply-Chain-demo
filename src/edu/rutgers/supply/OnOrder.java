package  edu.rutgers.supply;

import java.util.*;
import java.text.*;

import edu.rutgers.util.IllegalInputException;
import edu.rutgers.util.Config;
import edu.rutgers.util.ParaSet;

/** An auxiliary class used in various Pools to keep track of orders that have been placed by them but not yet filled. Provides functionality for order expiration, if desired.

    <p>Usage (e.g. in a Pool class, or in any other Receiver):
    <ul>Create an OnOrder object in the constructor.
    <li>In accept(), call  onOrder.subtract(now, a);
    <li>On regular intervals (e.g. in step() before placing new orders)
    call   onOrder.refresh(now);
    <li>After placing an order, call     onOrder.add(order);
    <li>For reporting, use onOrder.toString().
    </ul>
    
 */
public class OnOrder {
    /** After how many days unfilled orders expire (i.e. are treated
	as if they are never going to be fulfilled, so that it's OK to
	reorder).
     */
    final double expiration;

    public OnOrder(double _expiration) {
	expiration = _expiration;
    }
    

    /** Assumed to be ordered chronologically (by construction) */
    public Vector<Order> data = new Vector<>();
    /** Expired ordered that weren't fulfilled even with a delay. */
    Vector<Order> expired = new Vector<>();

    /** Sum of orders that were removed as "expired" */
    double totalRemoved = 0;
    /** The total size of shipments that came when there were no non-expired outstanding orders, and therefore
	were ascribed to expired orders. */
    double totalLateArrivals = 0;
    /** The sum of delay days for the above. Need to be divided by the above to obtain the avg weighted delay */
    double delaySum = 0;
    /** Arrivals that could not be associated with any non-expired or expired order */
    double totalOrphanArrivals = 0;
    
    
    public void add(Order e) {
	data.add(e);
    }

    /** Returns the list of orders that became "filled" amd removed
	from the unfilled order list during the most recent subtract()
	call
     */
    public Vector<Order> getLastFilled() {
	return lastFilled;
    }

    private Vector<Order> lastFilled = new Vector<>();

    /** @param amt the size of the newly arrived shipment, to be subtracted from the oldest outstanding (unexpired) orders.
	@return the part of amt that could not be applied to the outstanding (unexpired) orders, and had to be matched against the expired ones. (This does not include the amount that  corresponded to no order at all)
    */
    public double subtract(double now, double amt) {
	lastFilled.clear();
	while( amt>0 && data.size()>0) {
	    Order e = data.get(0);
	    double x = e.amount;
	    if (x <= amt) {
		e.filledDate = now;
		lastFilled.add(e);
		data.removeElementAt(0);
		amt -= x;		    
	    } else {
		data.get(0).amount -= amt;
		amt = 0;
	    }
	}

	double amtLate = amt;

	while( amt>0 && expired.size()>0) {
	    Order e = expired.get(0);
	    double x = e.amount;
	    double r =0;
	    if (x <= amt) {
		e.filledDate = now;
		lastFilled.add(e);
		expired.removeElementAt(0);
		r = x;
	    } else {		
		e.amount -= amt;
		r = amt;
	    }
	    //	    System.out.println("DEBUG: applying late shipment of " + r + " against expired order dated " + e.date);
	    amt -= r;		    
	    totalLateArrivals  += r;
	    double delay = now - (e.date + expiration);
	    if (delay<=0) throw new AssertionError();
	    delaySum += r*delay;
	}

	//if (amt>0) System.out.println("DEBUG: orphan shipment of " + amt);

	totalOrphanArrivals += amt;
	return amtLate - amt;
	
    }

    /** Removes any expired orders
	@return the total size of the removed orders
     */
    public Vector<Order> refresh(double now) {
	double removed = 0;
	Vector<Order> eo = new Vector<>();
	while( data.size()>0  && data.get(0).date + expiration <now) {
	    Order e = data.get(0);
	    data.removeElementAt(0);
	    removed += e.amount;
	    expired.add(e);
	    eo.add(e);
	    e.channel.sender.cancel(e);
	}
	totalRemoved += removed;
	return eo; // removed;
    }

    public double sum() {
	double s = 0;
	for(Order e: data) s += e.amount;
	return s;
    }

    public String toString() {
	String s = "" + (long)sum();
	Vector<String> v = new Vector<>();
	if (totalRemoved>0) v.add(""+(long)totalRemoved+" in expired orders");
	if (totalLateArrivals>0) v.add(""+(long)totalLateArrivals+" late arrivals (avg "+(delaySum/totalLateArrivals)+" days late)");
	if (totalOrphanArrivals>0) v.add(""+(long)totalOrphanArrivals+" orphan arrivals");
	if (v.size()>0) s += " (and also " + String.join(", ", v)+")";
	return s;
    }
    
}
   
