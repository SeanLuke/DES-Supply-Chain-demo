package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** The common base for various "warehouses", such as the Hospital/Pharmacies Pool or the Wholesale Pool.
 */
public class Pool extends sim.des.Queue
    implements Reporting, Named, BatchProvider 					    
{

    final double batchSize;

    /** Similar to typical, but with storage. In this case, it's batches of packaged drug  */
    protected final Batch prototype;

    protected final ParaSet para;

    /* 
    WholesalerPool,reorderPoint,0.75
    WholesalerPool,reorderQty,0.25
    */
    private boolean hasReorderPolicy = false;
    private double reorderPoint=0, reorderQty=0;

    public double getEverReceived() {
	return everReceived;
    }
    
    double everReceived = 0;
    final double  initial;

    protected Charter charter;

    Pool(SimState state, String name, Config config, Batch resource) throws IllegalInputException,
 IOException {
	this( state,  name,  config, resource, new String[0]);
    }
    
    Pool(SimState state, String name, Config config, Batch resource, String[] moreHeaders) throws IllegalInputException,
 IOException {
									    
	super(state, resource);	
	prototype = resource;
	setOffersImmediately(false); // shipping is driven by pulls from the downstream consumer

	setName(name);
	para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	initial = para.getDouble("initial");
	batchSize = para.getDouble("batch");
	initSupply(initial);

	expiredProductSink = new ExpiredSink(state,  resource, 365);
	stolenProductSink = new MSink(state,  resource);

	Double r = para.getDouble("reorderPoint", null);
	if (r!=null) {
	    hasReorderPolicy = true;
	    reorderPoint = r;
	    r =  para.getDouble("reorderQty", null);
	    if (r==null)  throw new  IllegalInputException("Element named '" + name +"' has reorder point, but no reorder qty");
	    reorderQty = r;
	}
	charter=new Charter(state.schedule, this);
	doChartHeader(moreHeaders);

    }

    
    /** Loads the Queue with the "initial supply", in standard size batches made today */
    private void initSupply(double initial) {
	int n = (int)Math.round( initial / batchSize);
	double now = state.schedule.getTime();
	for(int j=0; j<n; j++) {
	    Batch whiteHole = prototype.mkNewLot(batchSize, now);
	    Provider provider = null;  // why do we need it?
	    if (!accept(provider, whiteHole, 1, 1)) throw new AssertionError("Queue did not accept");
	}	
    }

    /** Stores information about one of the Pools from which this Pool get supplies. */
    private class Supplier {
	final //Pool
	    BatchProvider
	    src;
	final double fraction;
	/** Where does this supplier send stuff to? This can be either
	    this Pool itself (for immediate shipping), or a Delay
	    feeding into this Pool.
	    @param delayDistr The delay distribution, or null for immediate delivery
	*/
	final Receiver entryPoint;
	Supplier(BatchProvider _src, double _fraction,	    AbstractDistribution delayDistr) {
	    src = _src;
	    fraction = _fraction;

	    Receiver rcv = Pool.this;
	    if (delayDistr!=null) {
		Delay delay = new Delay(state,prototype);
		delay.setDelayDistribution(delayDistr);
		delay.addReceiver(rcv);
		rcv = delay;
	    }

	    entryPoint = rcv;
	}
    }

    /** Where the normal supply comes from */
    private Vector<Supplier> normalSuppliers = new Vector<>();
    /** Where back-orders can be placed, if the normal suppliers don't have enough material on hand */
    private Supplier backOrderSupplier = null;
    
    
    /** Looks at the "from" parameters in the ParaSet to identify the pools from which this pool will
	request resources.
<pre>
HospitalPool,from,WholesalerPool,0.95
HospitalPool,from,Distributor,0.05
HospitalPool,backOrder,WholesalerPool
</pre>
     */
    void setSuppliers(HashMap<String,Steppable> knownPools) throws IllegalInputException {
	normalSuppliers.setSize(0);
	for(int j=0; j<10; j++) {
	    String key = "from" + j;
	    String key2 = "delay" + j;	    
	    Supplier sup = mkSupplier(key, key2, knownPools, false);
	    if (sup!=null)     normalSuppliers.add(sup);
	}

	String key = "backOrder";
	String key2 = "delayBackOrder";
	backOrderSupplier = mkSupplier(key, key2, knownPools, true);				
    }

    /** Initializes a supplier based on a line from the ParaSet */
    private Supplier mkSupplier(String key, String key2, HashMap<String,Steppable> knownPools, boolean bePool)
	throws IllegalInputException {
	Vector<String> v = para.get(key);
	if (v==null) return null;
	if (v.size()!=2) throw new IllegalInputException("Invalid number of fields for " + getName() +"," + key);
	String supName = v.get(0);
	Steppable q =  knownPools.get(supName);
	if (q==null) throw new IllegalInputException("Unknown supply pool name ("+supName+") for " + getName() +"," + key);
	if (!(q instanceof BatchProvider)) throw new IllegalInputException("Supplier ("+supName+") is not a BatchProvider, for " + getName() +"," + key);
	if (bePool && !(q instanceof Pool)) throw new IllegalInputException("Supplier ("+supName+") is not a Pool, for " + getName() +"," + key);
	BatchProvider p = (BatchProvider)q;
	double frac =    para.parseDoubleEx(key,  v.get(1));
	Receiver rcv = this;
	AbstractDistribution dis = para.getDistribution(key2,state.random);
	return new Supplier(p, frac, dis);
    }
    

    /** Keeps track of the amount of product that has been discarded because we discovered
	that it was too close to expiration.  */
    ExpiredSink expiredProductSink;
  

    /** How mauch has  been demanded from this pool by its customers */
    double everSent = 0;

    /** Records, for each day, how much has been sent */
    Vector<Double> dailyDemandHistory = new Vector<Double>();
    double demandedToday=0, sentToday=0;
    
    /** Records today's "sent" amount */
    synchronized private void recordDemand(double demand) {
	double t = state.schedule.getTime();
	int j = (int)Math.round(t);
	if (dailyDemandHistory.size()<=j) dailyDemandHistory.setSize(j+1);
	Double _has = dailyDemandHistory.get(j);
	double has = (_has==null)? 0: _has;
	dailyDemandHistory.set(j, has + demand);
	demandedToday += demand;
    }

    /** Computes the total "demand" value over the last 30 days.
     */
    double getLastMonthDemand() {
	return getRecentDemand(30);
    }

    /** Get the demand for the last n days. 
	FIXME: this could be made more efficient by keeping the running totals for each day,
	but we just don't bother. */
    double getRecentDemand(int n) {
	double t = state.schedule.getTime();
	int j = (int)Math.round(t);
	if (dailyDemandHistory.size()<=j) dailyDemandHistory.setSize(j+1);
	double sum=0;
	for(int i=j; i>=j-n && i>0; i--) {
	    Double has = dailyDemandHistory.get(i);
	    if (has!=null)  sum+= dailyDemandHistory.get(i);
	}
	return sum;
    }
    
    /** Handles the request from a downstream receiver (such as the EndConsumer) to send to it 
	a number of batches, totaling at least a specified amount of stuff. Discards any 
	expired (or near-expired) batches identified during the process.
	@param amt the requested amount (in units)
	@return the actually sent amount, in units. (Can be a bit more
	than amt due to batch size rounding, or it  can be less due to the
	shortage of product).
     */
    public double feedTo(Receiver r, double amt) {
	return feedTo(r, amt, true);
    }
    
    public double feedTo(Receiver r, double amt, boolean doRecordDemand) {
	double sent = 0;
   
	Batch b;
	while(getAvailable()>0 && sent<amt &&
	      (b = expiredProductSink.getNonExpiredBatch(this, entities))!=null) {
	    if (!offerReceiver(r, b)) throw new IllegalArgumentException("Expected acceptance by " + r);
	    sent += b.getContentAmount();
	    entities.remove(b);
	}

	everSent += sent;
	sentToday += sent;
	// FIXME: double-recording may occur in an un-fulfilled instant order followed by a back-order
	if (doRecordDemand) recordDemand(amt);
	return sent;
    }


    /** Not-yet-fulfilled back orders */
    HashMap<Receiver,Double> needToSend = new HashMap<>();

    /** The Pool receives an order for something that it does not
	have, and files it for later filling */
    void backOrder(Receiver r, double amt) {
	Double x = needToSend.get(r);
	needToSend.put(r,  x==null? amt: x+amt);
	recordDemand(amt);
    }

    /** Checks all outstanding back-order requests, and fulfills them to the extent possible.
     */
    protected void fillBackOrders() {
	for(Receiver rcv:  needToSend.keySet()) {
	    double amt = needToSend.get(rcv);
	    double sent = feedTo(rcv,  amt, false);
	    amt -= sent;
	    if (amt<0) amt=0;
	    needToSend.put(rcv,amt);
	}
    }
    
    /** How much stuff is stored by this pool? 
	@return the total content of the pool (in units)
     */
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


    
    /** Performs a reorder, if required; then fills any outstanding back orders. */
    public void step​(sim.engine.SimState state) {
	reorderCheck();
	fillBackOrders();

	doChart​(new double[0]);
    }

    private double receivedToday=0;

    
    /** The outstanding order amount: the stuff that this pool has ordered, but which has not arrived yet.  FIXME: it would be better to have separate vars for separate suppliers
 */
    private double onOrder = 0;

    /** This is called from a supplier when it ships a batch over. 
	@param amount a Batch object
     */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	if (((Demo)state).verbose) System.out.println("At t=" + state.schedule.getTime() + ", " +  getName()+ " receiving "+
						      atLeast + " to " +  atMost + " units of " + amount );
	//+					      ", while delay.ava=" + supplierDelay.getAvailable());
	//double s0 = getAvailable();
	double a = ((Batch)amount).getContentAmount();
	boolean z = super.accept(provider, amount, atLeast, atMost);
	if (!z) throw new AssertionError("Pool " + getName() + " refused delivery. This ought not to happen!");

	onOrder -= a;
	if (onOrder < a) { // they have over-delivered
	    onOrder=0;
	}
	everReceived += a;
	receivedToday += a;
	
	return z;
    }

    double orderedToday = 0;

    /** Checks if this pool needs stuff reordered, and makes an order if needed */
    private void reorderCheck() {
	if (!hasReorderPolicy) return;
    	double t = state.schedule.getTime();
	double lms = getLastMonthDemand();
	if (Demo.verbose) System.out.println("As of " + t+", " + getName() + " has sent " + lms + " units over the last month");
	double have =  getContentAmount() + onOrder;
	if (have > reorderPoint * lms) return;
	double needed = Math.round(reorderQty * lms);
	double unfilled = needed;

	for(Supplier sup: normalSuppliers) {
	    double amt = sup.fraction * needed;
	    double sent = sup.src.feedTo(sup.entryPoint, amt);
	    unfilled -= sent;
	    onOrder += sent;
	}

	// if some suppliers were short, try to compensate through others
	for(Supplier sup: normalSuppliers) {
	    if (unfilled <= 0) break;
	    double sent = sup.src.feedTo(sup.entryPoint, unfilled);
	    unfilled -= sent;
	    onOrder += sent;
	}

	// if still unfilled, back-order
	if (unfilled > 0) {
	    ((Pool)backOrderSupplier.src).backOrder(backOrderSupplier.entryPoint, unfilled);
	    onOrder += unfilled;	    
	}

	orderedToday = needed;
	
    }
    

    public String report() {	
	String s = "[" + getName()+ " has received " + everReceived + " u (including initial="+initial+" u), has sent "+everSent+" u";

	if (expiredProductSink.everConsumed >0) {	
	    s += ". Discarded as expired=" + expiredProductSink.everConsumedBatches +  " ba";
	}
	if (stolenProductSink.everConsumed >0) {
	    s +=  ". Stolen=" + stolenProductSink.everConsumedBatches +  " ba";
	}
	s += "]";
       return wrap(s);
   }

    /** Used when modeling disruptions */
    private MSink stolenProductSink;
    //private double stolen=0;
    //private int stolenBatches=0;


    /** Simulates theft or destruction of some of the product stored in 
	this pool
	@param The amount of product (units) to destroy.
	@param return The amount actually destroyed
    */
    protected synchronized double deplete(double amt) {
	double destroyed = 0;
	if (getTypical() instanceof Batch) {
	    while(destroyed<amt && getAvailable()>0) {
		Batch b=(Batch)entities.getFirst();
		if (!offerReceiver( stolenProductSink, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		entities.remove(b);
		destroyed += b.getContentAmount();
		//stolenBatches ++;
	    }
	} else {
	    if (getAvailable()>0) {
		double ga0 = getAvailable();
		offerReceiver(stolenProductSink, amt);
		destroyed = ga0 - getAvailable();
	    }
	}
	//stolen += destroyed;
	return  destroyed;		
    }

    void doChart​Header(String... moreHeaders) {
	String[] a = {"stock","orderedToday"  ,"receivedToday", "demandedToday","sentToday"};
	String[] b = Arrays.copyOf(a, a.length + moreHeaders.length);
	int j=a.length;
	for(String x:  moreHeaders) { b[j++] = x; }

	charter.printHeader(b);
    }

    /** Writes a line to the time series file. Call this from step() */
    void doChart​(double... moreValues) {
	double stock =  getContentAmount();        
	double[] a = {stock,orderedToday,receivedToday,demandedToday,sentToday};
	double[] b = Arrays.copyOf(a, a.length + moreValues.length);
	int j=a.length;
	for(double x:  moreValues) { b[j++] = x; }
	
	charter.print(b);
	receivedToday=0;
	demandedToday=0;
	sentToday=0;
	orderedToday=0;
    }

    
}

