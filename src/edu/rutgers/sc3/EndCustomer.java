package  edu.rutgers.sc3;

import edu.rutgers.supply.*;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models the End Customer Pool. It requests and receives Batches of
    packaged product on a daily schedule from a specified upstream
    pool.

    It is implemented as a Sink, because once a batch of product has arrived
    here, nothing much needs to be done with it anymore, other than counting it.

    <p>--------
    <ul>
    <li>
    step() generates demand, puts new orders into OnOrder, and calls request() on arrayAssembly

    <li>
    arrayAssembly sends shipments to EndCustomer ; EndCustomer.accept() adjusts OnOrder

    <li>Support performance metrics based on the time it took to fully satsify already fully filled order, and the time during which still-open orders have been outstanding. To do that, we can scan the lists of "closed" and "open" orders.
    </ul>
    
 */
public class EndCustomer extends MSink implements Reporting {

    double now() {
	return  state.schedule.getTime();
    }

    /** What's the probability that on a given day an order comes in,
	e.g. 1/90 for the average of 4 orders per year.
     */
    private final double orderProbability;
    /** The order size is drawn from this probability */
    private final AbstractDistribution orderSizeDistribution;

    
    /** Similar to typical, but with storage. In this case, it's batches of packaged drug  */
    private final Batch prototype;
    
    private final ParaSet para;
    
    private double everOrdered = 0;

    /** 
	@param resource The batch resource consumed here
     */
    public EndCustomer(SimState state, String name, Config config,
		 Batch resource) throws IllegalInputException {
       
	super(state,resource);
	setName(name);
	para = config.get(name);
	if (para==null) throw new IllegalInputException("Config file has no data for unti=" + name);
	prototype = resource;
	orderProbability = para.getDouble("orderProbability");
	orderSizeDistribution = para.getDistribution("orderSize",state.random);	
    }

    void linkUp(HashMap<String,Steppable> knownPools) throws IllegalInputException {
	String sourceName = para.getString("source", null);

	if (sourceName==null) {	
	    throw new  IllegalInputException("No " + getName() + ",source");
	} else {

	    Steppable _from =  knownPools.get(sourceName);
	    if (_from==null || !(_from instanceof BatchProvider2)) throw new  IllegalInputException("There is no provider unit named '" + sourceName +"', in " + getName() + ",source");
	    source = (BatchProvider2)_from;
	}
	channel = new Channel(source, this, getName());

	onOrder = new OnOrder( para.getDouble("orderExpiration", Double.POSITIVE_INFINITY ));
    }

    /** Specifies from where we take stuff to satisfy the demand */
    void setSource(BatchProvider2 _source) {
	source = _source;
    }

    /** The pool from where  we take stuff to satisfy the demand */
    private BatchProvider2 source;
    private Channel channel;
    

    /** The amount that has been received previously in excess of what had
	been ordered. (Because the supplier has over-produced).
	It will be carried forward and deducted from the next day's demand.
     */
    private double hasExtra = 0;

    //    private double  totalUnsatisfiedDemand = 0;


    /** Orders are moved here once they have been fully filled */
    Vector<Order> filledOrders = new Vector<>();

    private OnOrder onOrder;

    
    /** Consumes product out of the Hospital/Pharmacy pool on a certain schedule */
    public void step(sim.engine.SimState state) {
	try {
	boolean hasOrder = state.random.nextBoolean(orderProbability);

	if (hasOrder) {
	    double demand = orderSizeDistribution.nextDouble();
	    if (demand<=0) throw new AssertionError("Negative or zero demand");
	    if (Math.round(demand)!=demand) throw new AssertionError("Non-integer demand");
	
	    Order order = new Order(now(), channel, demand);
	    everOrdered += demand;
	    
	    double reduceBy = Math.min(hasExtra,order.amount);
	    order.amount -= reduceBy;
	    hasExtra -= reduceBy;
	    if (order.amount==0) {
		order.filled(now());
		filledOrders.add(order);
	    } else {
		source.request(order);
		onOrder.add(order);
	    }
	}

	/*
	double sent = source.feedTo(this, demand);
	if (sent > demand) {
	    hasExtra += (sent - demand);
	} else {
	    totalUnsatisfiedDemand  += (demand - sent);
	}
  	*/
	} finally {
	    dailyChart();
	}
    }


    public double getEverReceived() {
    	return everConsumed;
    }

    /** The amount ever received by the pool
     */
    double// everReceived=0,
	receivedToday=0;
    
    /** This is triggered when the Production nodes sends ordered
	product here */
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	if (!(resource instanceof Batch)) throw new IllegalArgumentException("EndCustomer expects to only received Batches");

	Batch b = (Batch)resource;
	double a = b.getContentAmount();

	double late = onOrder.subtract(now(), a);
	if (late>0) {
	    //System.out.println("DEBUG: "+getName()+", at=" + now+", just processed a late shipment of " + late);
	}

		
	//	everReceived += a;
	receivedToday += a;

	
	//	everReceivedBad += ((Batch)resource).getLot().illicitCount;
	boolean z = super.accept( provider, resource,  atLeast, atMost);
	return z;
    }

    /** Overall stats on spacecraft waiting for their panels */
    public static class Stats {
	public long sumN = 0;
	public double sumT = 0;
	public double avgT = Double.NaN;
	public int cnt=0;
	
	public Stats(Vector<Order> orders, double now) {	    
	    for(Order order: orders) {
		double t = now;
		if (t==Double.NaN) {
		    if (order.filledDate==Double.NaN) throw new AssertionError("No filled date");
		    t = order.filledDate;
		}
		sumT += t-order.date;
		sumN += order.amount0;
		cnt++;
	    }
	    if (sumN>0) avgT = sumT/sumN;
	}

	/** Adds the other set of stats to this one */
	void add(Stats o) {
	    cnt += o.cnt;
	    sumN += o.sumN;
	    sumT += o.sumT;
	    if (sumN>0) avgT = sumT/sumN;
	}
    }


    /** Computes the avg waiting time for filled orders.
	@return  the avg waiting time for filled orders, or NaN if none has been filled yet
     */
    public Stats avgWaitingFilled() {
	return new Stats(filledOrders, Double.NaN);
    }

    public Stats avgWaitingUnfilled() {
	return new Stats(onOrder.data, now());
    }

    
    public String report() {
	String s = getName() + ": Ordered=" + everOrdered + ", received=" + everConsumed;
	Stats awf=avgWaitingFilled(), awu=avgWaitingUnfilled();
	Vector<String> v = new Vector<>();

	
	if (awf.cnt>0) 	v.add( " for "+awf.cnt+" filled orders " + awf.avgT + " days");
	if (awu.cnt>0) 	v.add( " for "+awu.cnt+" unfilled orders " + awf.avgT + " days so far");


	if (v.size()>0) s += ". Avg waiting time" + String.join(",", v) + ".";
	return s;
	//super.report() + "\n" +
	//  "Received bad units=" + 	everReceivedBad + ". " +
	//	    "Total unfulfilled demand=" + totalUnsatisfiedDemand + " u";
    }


    
    /** Writes this days' time series values to the CSV file. 
	Does that for the safety stocks too, if they exist.
	Here we also check for the inflow anomalies in all
	buffers.  This method needs to be called from Production.step(),
	to ensure daily execution.
    */
    private void dailyChart() {
	/*
	double releasedAsOfToday =getReleased();

	double releasedToday = releasedAsOfToday - releasedAsOfYesterday;
	releasedAsOfYesterday = releasedAsOfToday;
	
	//double[] data = new double[2 + 2*inputStore.length];
	double[] data = new double[2 + 3*inputStore.length];
	int k=0;
	data[k++] = releasedToday;
	data[k++] = sumNeedToSend(); // (startPlan==null)? 0 : startPlan;
	for(int j=0; j<inputStore.length; j++) {
	    data[k++] = inputStore[j].getContentAmount();
	    data[k++] = inputStore[j].receivedTodayFromNormal;
	    data[k++] = inputStore[j].receivedTodayFromMagic;
	    inputStore[j].clearDailyStats();
	}	
	
	//for(int j=0; j<inputStore.length; j++) {
	//    data[k++] = inputStore[j].detectAnomaly()? 1:0;
	//}
   
	charter.print(data);
		
	//for(InputStore p: inputStore) {
	//    if (p.safety!=null) p.safety.doChart(new double[0]);
	//}

	*/
	//	orderedToday=0;
	//	receivedToday = 0;
 
    }
     
    
}
