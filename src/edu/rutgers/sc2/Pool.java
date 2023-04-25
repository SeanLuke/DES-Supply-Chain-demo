package  edu.rutgers.sc2;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** A Pool models a place where a product is stored, and from which it
    can be "pulled" by other elements of a supply chain. A Pool is
    implemented as a DES Queue, with additional methods used for
    processing pull requests, and for automatic replenishment when the 
    level falls below a certain point.

    <p> In SC-1, the Pool class is the common base for various
    "warehouses", such as the Hospital/Pharmacies Pool or the
    Wholesale Pool.

    <P>
    A Pool typically stores a Batch product (thus, <tt>prototype</tt> is Batch),
    but CountableResource (for fungible products, such as packing material)
    is supported too.

    <P>//FIXME: it may be better to refactor this class, having AbstractPool,
    with more generic functionality,  as the common parent of 
    Pool (for post-production pools) and SafetyStock
 */
public class Pool extends sim.des.Queue
    implements Reporting, Named, BatchProvider {


    double now() {
	return  state.schedule.getTime();
    }
    
    final double batchSize;

    /** This can be Batch, or CountableResource. It is similar to
	Provider.typical, but (in the Batch case) it also has information
	about the underlying resource in its storage[]. In the
	Wholesaler Pool etc, this represents batches of packaged
	drug. */
    protected final Resource prototype;
    Resource getPrototype() { return prototype; }

    protected final ParaSet para;

    // Controlled by configuration parameters, e.g.:
    // WholesalerPool,reorderPoint,0.75
    // WholesalerPool,reorderQty,0.25
   
    private boolean hasReorderPolicy = false;
    protected double reorderPoint=0;
    private double reorderQty=0;

    /** How much this pool has ordered from its own suppliers, for its own
	replenishment */
    protected double everOrdered=0;
    
    public double getEverReceived() {
	return everReceived;
    }

    /** The amount ever received by the pool (not including the amount
	"received" during the initialization process in the
	constructor)
     */
    double everReceived = 0;
    protected final double  initial, initialReceived, targetLevel;
    public double getInitial() {
	return initial;
    }
   
    protected Charter charter;

    /** How much stuff is stored here. The value should be the same as given by
	getContentAmount(), but without scanning the entire buffer */
    protected double currentStock=0;

    Pool(SimState state, String name, Config config, Resource resource) throws IllegalInputException,
 IOException {
	this( state,  name,  config, resource, new String[0]);
    }

    enum ReorderMode {
	Mode1
    };

    private ReorderMode mode;
    
    /** @param moreHeaders The names of additional columns to be printed in the CSV time series file. If this array is not empty, you also need to provide the values of these additional variables in the doChart() calls.
     */
   Pool(SimState state, String name, Config config,
	 Resource resource, String[] moreHeaders) throws IllegalInputException,  IOException {
									    
	super(state, resource);	
	prototype = resource;
	setOffersImmediately(false); // shipping is driven by pulls from the downstream consumer

	setName(name);
	para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for pool named '" + name +"'");

	mode = para.getEnum(ReorderMode.class, "mode", ReorderMode.Mode1);
	
	initial = para.getDouble("initial");
	targetLevel = para.getDouble("targetLevel", initial);
	batchSize = para.getDouble("batch");
	boolean aged = para.getBoolean("initial.aged", false);
	initialReceived = initSupply(initial, aged);
	everReceived = 0; // not counting the initial supply

	expiredProductSink = (resource instanceof Batch) ?
	    new ExpiredSink(state,  (Batch)resource, 365) : null;

	stolenProductSink = new MSink(state,  resource);

	Double r = para.getDouble("reorderPoint", null);
	if (r!=null) {
	    hasReorderPolicy = true;
	    reorderPoint = r;

	    //	    if (this instanceof SafetyStock) {
		// does not need reorderQty, since it's dynamic
	    //} else
	    {
		if (mode==ReorderMode.Mode1) {
		    // uses reorderLevel, not Qty
		} else {
		    r =  para.getDouble("reorderQty", null);
		    if (r==null)  throw new  IllegalInputException("Element named '" + name +"' has reorder point, but no reorder qty");
		    reorderQty = r;
		}
	    }
	}
	charter=new Charter(state.schedule, this);
	doChartHeader(moreHeaders);


    }

    
    /** Instantly loads the Queue with the "initial supply", in standard size
	batches made today.

	@param aged If true, load the pool with "aged" items, rather
	than freshly made ones, to simulate a steady-state
	situation. Only supported for EE.
    */
    private double initSupply(double initial, boolean aged) {
	//System.out.println("DEBUG:" + getName() + ", initSupply(" +initial+") in");
	double sent=0;
	if (prototype instanceof Batch) {
	    double now = state.schedule.getTime();

	    if (aged) {
		for(long j=0; j<(long)initial; j++) {
		    Batch eeb = ((Batch)prototype).mkNewLot(1, now);
		    EE ee = new EE(eeb, true);
		    Provider provider = null;  // why do we need it?
		    if (!accept(provider, ee, 1, 1)) throw new AssertionError("Queue did not accept");
		    sent += 1;
		}		
	    } else {
		int n = (int)Math.round( initial / batchSize);
		Provider provider = new Source(state, prototype);  // why do we need it?
		provider.setName("initial");
		for(int j=0; j<n; j++) {
		    Batch whiteHole = ((Batch)prototype).mkNewLot(batchSize, now);
		    if (!accept(provider, whiteHole, 1, 1)) throw new AssertionError("Queue did not accept");
		    sent += batchSize;
		}
	    }

	} else {
	    if (aged) throw new IllegalArgumentException("Cannot 'age' CountableResource");
	    CountableResource b = new CountableResource((CountableResource)prototype, sent=initial);
	    Provider provider = null;  // why do we need it?
	    if (!accept(provider, b, initial, initial)) throw new AssertionError("Queue did not accept");
	}	
   	//System.out.println("DEBUG:" + getName() + ", initSupply(" +initial+") out");
	return sent;
    }

    /** An auxiliary structure that stores information about one of
	the Pools from which this Pool gets replenished. It exists so
	that we can configure connections between pools based on their
	descriptions in the config file.
    */
    private class Supplier {
	final //Pool
	    BatchProvider
	    src;
	final double fraction;
	/** Where does this supplier send stuff to? This can be either
	    this Pool itself (if shipping is immediate shipping), 
	    or a Delay feeding into this Pool.
	*/
	final Receiver entryPoint;

	public String toString() {

	    return "("+((Named)src).getName()+", f="+fraction+")";
	}
	
	/**
	    @param delayDistr The delay distribution, or null for immediate delivery
	*/
	Supplier(BatchProvider _src, double _fraction,	AbstractDistribution delayDistr) {
	    src = _src;
	    fraction = _fraction;

	    Receiver rcv = Pool.this;
	    if (delayDistr!=null) {
		Delay delay = new Delay(state,prototype);
		delay.setName(rcv.getName() + ".inputDelay");
		delay.setDelayDistribution(delayDistr);
		delay.addReceiver(rcv);
		rcv = delay;
	    }

	    entryPoint = rcv;
	}
    }

    /** Where the normal supply comes from. Each entry comes with a real number,
	which, depending on the mode, may be interepreted as a fraction or
	a probability.
    */
    private Vector<Supplier> normalSuppliers = new Vector<>();
    /** Where back-orders can be placed, if the normal suppliers don't have enough material on hand */
    private Supplier backOrderSupplier = null;

    /** If this is not null, a "parallel order" (typically, a work order,
	in the amount equal to the usual pull order) is sent here. This is used
	e.g. by sc2.HEP. The amount of parallel order is not added to "onOrder".
     */
    private Supplier parallelSupplier = null;
    
    /** Looks at the "from" parameters in the ParaSet to identify the pools from which this pool will	request resources.
<pre>
WholesalerPool,from1,Distributor,0.9
WholesalerPool,from2,UntrustedPool,0.1
HospitalPool,backOrder,WholesalerPool
</pre>

@param knownPools The table of existing supply chain elements, from Demo.addedNodes
     */
    void setSuppliers(HashMap<String,Steppable> knownPools) throws IllegalInputException {
	normalSuppliers.setSize(0);
	for(int j=0; j<10; j++) {
	    String key = "from" + j;
	    String key2 = "delay" + j;	    
	    Supplier sup = mkSupplier(key, key2, knownPools, false);
	    if (sup!=null)     normalSuppliers.add(sup);
	}

	if (Demo.verbose) System.out.println("DEBUG: For Pool " + getName() +", the normal suppliers are " + Util.joinNonBlank("; ", normalSuppliers));

	
	String key = "backOrder";
	String key2 = "delayBackOrder";
	backOrderSupplier = mkSupplier(key, key2, knownPools, true);

	key = "parallelOrder";
	key2 = "delayParallelOrder";
	parallelSupplier =  mkSupplier(key, key2, knownPools, false);
    }

    /** Initializes a supplier based on a line from the ParaSet. Example:
<pre>
	HospitalPool,from1,WholesalerPool,1.00
#HospitalPool,from2,Distributor,0.00
HospitalPool,backOrder,WholesalerPool,1.0
HospitalPool,delay1,Triangular,7,10,15
HospitalPool,delay2,Triangular,7,10,15
HospitalPool,delayBackOrder,Triangular,7,10,15
</pre>

@param bePool If true, requires that the specified supplier is a Pool (and not some other node)
 */
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
	//Receiver rcv = this;
	AbstractDistribution dis = para.getDistribution(key2,state.random);
	return new Supplier(p, frac, dis);
    }
    

    /** Keeps track of the amount of product that has been discarded because we discovered
	that it was too close to expiration. This variable is null for fungible products,
	which have no expiration dates.
    */
    ExpiredSink expiredProductSink;
  

    /** How much has  been sent by this pool to its customers */
    double everSent = 0;
    double sentToday=0;
    
    /** Records, for each day, how much has been demanded from this pool*/
    Vector<Double> dailyDemandHistory = new Vector<Double>();
    double demandedToday=0;
    
    /** Records today's "demanded" amount */
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

    /* // This was used to helpt to figure how Delay really works in some sticky
       // situations. Not needed in production runs.
    private String reportDelayState(Delay delay) {
	DelayNode[] nodes = delay.getDelayedResources();
	Vector<String> v = new Vector();
	v.add("DS: " + nodes.length + " nodes; TDR=" + delay.getTDR() +", ava=" +delay.getAvailable());
	for(DelayNode node: nodes) {
	    int n = 0;
	    for(DelayNode z=node; z.next!=null; z=z.next) n++;
	    String s = "DS: " + node.getTimestamp();
	    if (n>0) s += "; extra " + n;
	    v.add(s); 
	}	
	return String.join("\n", v);
    }
    */


    private double deficit() {
	return everReceived + initialReceived - everSent - currentStock;
    }
    
    /** Process a request from a consumer (a downstream pool) to give
	it some stuff. To the extent stuff is available, 
	moves stuff from this pool directly to another pool etc,
	or to a Delay object that models shipping to someplace.

	<p>
	If we're offering multiple batches via a Delay, we switch the Delay to 
	the "UsesLastDelay" mode, in order to simulate shipping multiple
	pallets on the same truck or ship. (This is also good for 
	computational efficiency).

	@param r Where to put the stuff. This can be a Pool or a
	SimpleDelay, or something else.

	@param amt How much stuff has requested by the consumer. The entire
	amount is recorded as (part of) today's demand; the entire amount,
	or part of it (as much as available) is sent out.

	@param doRecordDemand  True if this is the "first call" (likely
	from another chain element), so that the entire demanded amount
	can be recorded in the historical record. False in "secondary calls",
	which try to fill back orders, whose original amount was already
	added to the record.

	@return The amount actually sent. If the amount was smaller
	than requested, the caller will decide what to do next,
	i.e. either back-order from the same pool, or try to send
	an order somewhere else.
    */
    public double feedTo(Receiver r, double amt, boolean doRecordDemand) {
	final boolean consolidate = true;

	if (amt<=0) return 0;
	double sent = 0, expired=0;
	double now = state.schedule.getTime();

	if (prototype instanceof Batch) {
		    
	    Batch b;
	    Delay delay = (consolidate && (r instanceof Delay))? (Delay)r: null;
	    int n = 0;
	    
	    if (delay!=null) {
		delay.setDropsResourcesBeforeUpdate(false);
		//double now = state.schedule.getTime();
	    }

	    double expired0 = expiredProductSink.getEverConsumed();
	    while(//getAvailable()>0 &&
		  sent<amt &&
		  (b = expiredProductSink.getNonExpiredBatch(this, entities))!=null) {
		double a = b.getContentAmount();
		if (!offerReceiver(r, b)) throw new IllegalArgumentException("Expected acceptance by " + r);
		n++;
		if (n==1 && delay!=null) {
		    //delay.setFreezingDelay(true);
		    delay.setUsesLastDelay(true);
		}
		sent += a;
		entities.remove(b);

	    }
	    expired = expiredProductSink.getEverConsumed() - expired0;
	    if (delay!=null) {
		delay.setUsesLastDelay(false);
	    }
	    currentStock -= (sent + expired);

	    if (Demo.verbose && getName().equals("dsDC") && r.getName().startsWith("dsDP")) {
		System.out.println("DEBUG: at t="+ now()+", "  + getName()+".feedTo("+r.getName()+") sent=" + sent);				   
	    }

	} else if (prototype instanceof CountableResource) {
	    offerReceiver(r, amt);
	    currentStock = getAvailable();
	} else throw new AssertionError();
	everSent += sent;
	sentToday += sent;
	// FIXME: double-recording may occur in an un-fulfilled instant order followed by a back-order
	//if (doRecordDemand) recordDemand(amt);
	// In SC-2 we only record the sent amount, because the consumer will
	// make a separate backOrder call for the unfilled amount, and that
	// amount will be recorded at that point
	if (doRecordDemand) recordDemand(sent);
	    

	return sent;
    }


    /** Not-yet-fulfilled back orders. For each Receiver this map
	shows how much (0 or more) we still need to send to it.
     */
    private HashMap<Receiver,Double> needToSend = new HashMap<>();

    double sumNeedToSend() {
	double s=0;
	for(Double x: needToSend.values()) {
	    if (x!=null) s+=x;
	}
	return s;
    }
	
    
    /** The Pool receives an order for something that it does not
	have, and files it for later filling. This method can be
	used once the caller knows that the Pool cannot ship anything
	today anymore.
    */
    void backOrder(Receiver r, double amt) {
	Double x = needToSend.get(r);
	needToSend.put(r,  x==null? amt: x+amt);
	recordDemand(amt);
    }
    
    /** Checks all outstanding back-order requests, and fulfills them
	to the extent possible.
     */
    protected void fillBackOrders() {
	for(Receiver rcv:  needToSend.keySet()) {
	    double amt = needToSend.get(rcv);
	    if (amt<=0) continue;
	    double sent = feedTo(rcv,  amt, false);
	    amt -= sent;
	    if (amt<0) amt=0;
	    needToSend.put(rcv,amt);
	}
    }
    
    /** How much stuff is stored by this pool? 
	@return the total content of the pool, i.e. the sum of sizes
	of all stored batches (in units)
     */
    public double getContentAmount()        {
        if (resource != null) {  // fungible
            return resource.getAmount();
	} else if (entities != null) {  // batch
	    /*
	    double sum = 0;
	    for(Entity e: entities) {
		sum +=  (e instanceof Batch)? ((Batch)e).getContentAmount() : e.getAmount();
	    }
	    if (sum!=currentStock) throw new AssertionError("currentStock=" + currentStock);
	    */
            return currentStock;
	}  else throw new  AssertionError("");
    }


    
    /** Performs a reorder, if required; then fills any outstanding back orders, and prints the chart entry. */
    public void step(sim.engine.SimState state) {

	double now = getState().schedule.getTime();
	//	if (Demo.verbose) System.out.println("DEBUG:" + getName() + ", t="+now+", step");
	
	reorderCheck();
	fillBackOrders();
	doChart(new double[0]);
    }

    /** The amount of stuff received since the most recent daily
	chart-writing.  This is incremented at every accept, and
	reduced to 0 at the chart-writing step.
    */
    protected double receivedToday=0;

    /** Used in computing the average age of batches coming in on a given day */
    protected double batchesReceivedToday=0, sumOfAgesReceivedToday=0;
    
    /** The outstanding order amount: the stuff that this pool has ordered, but which has not arrived yet.  It is used so that the pool does not try to repeat its order daily until the orignal order arrives.
      FIXME: it would be better to have separate vars for separate suppliers
 */
    protected double onOrder = 0;

    /** This is called from a supplier (or the associated Delay) when
	a batched shipped to this pool arrives.

	Every piece of resource getting into this pool goes through this method;
	this is why we have currentStock increment done in here.
	@param amount a Batch object
     */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {

	double a = Batch.getContentAmount(amount);


	//System.out.println("DEBUG:" + getName() + ", stock=" +getContentAmount() +", accepting " +a + " from provider=" + provider);
	
	boolean z = super.accept(provider, amount, atLeast, atMost);
	if (!z) throw new AssertionError("Pool " + getName() + " refused delivery. This ought not to happen!");
	if ((amount instanceof CountableResource) && amount.getAmount()>0) throw new AssertionError("Incomplete acceptance by a Pool. Our pools ought not to do that!");

	if (provider instanceof SimpleDelay) { // Received a non-immediate delivery. This way we exclude deliveries from the repair pool to HEP, which should not be counted in onOrder
	    // FIXME: need to finesse this, in case we have immediate orders (no delay) betweeen pools
	    onOrder -= a;
	    if (onOrder < a) { // they have over-delivered
		onOrder=0;
	    }
	}


	if (Demo.verbose && getName().equals("dsDP")) {
	    String s= (provider==null) ? "null" :provider.getName();
	    if (s==null) s = "unknown";
	    if (!s.equals("initial")) {
		//System.out.println("DEBUG: at t=" +now()+", " + getName()+".accept("+s+", "+a+")");
	    }
	}


	
	everReceived += a;
	receivedToday += a;
	
	currentStock += a;

	if (amount instanceof Batch) {
	    double now = state.schedule.getTime();
	    batchesReceivedToday ++;
	    sumOfAgesReceivedToday += (now - ((Batch)amount).getLot().getEarliestAncestorManufacturingDate());
	}

	//double now = state.schedule.getTime();
	//if (deficit()!=0) System.out.println("accept("+a+") done: t="+now+". "+report());

	//	System.out.println("DEBUG:" + getName() + ", everReceived=" +everReceived +", receivedToday=" + receivedToday + ", currentStock=" + currentStock);

	
	return z;
    }

    /** Chooses a supplier at random, using sup.fraction values as probabilities */
    static private Supplier pickRandomSupplier(Vector<Supplier> suppliers, ec.util.MersenneTwisterFast random) {
	double probSum = 0;
	for(Supplier sup: suppliers) {
	    probSum += sup.fraction;
	}
	if (probSum <=0) throw new AssertionError();

	double who = random.nextDouble() * probSum, s = 0;
	
	for(Supplier sup: suppliers) {
	    s += sup.fraction;
	    if (s >= who) return sup;
	}
	throw new AssertionError();
	//return null;
    }
    
    /** Reorder Mode 1: simple MTS. The supplier is chosen randomly,
	and the entire order goes to it (with a back-order portion,
	if necessary). Additionally, if the config prescribes it,
	a parallel order is sent to the "parallel" supplier (i.e.
	a manufacturing order in addition to a pull order).
     */
    protected void reorderCheck1() {
	if (targetLevel <= reorderPoint) throw new IllegalArgumentException(getName() + ": illogical MTS plan, with targetLevel=" + targetLevel + "<=reorderPoint=" + reorderPoint);
	
	double now = getState().schedule.getTime();
	if (Demo.verbose && currentStock != getContentAmount()) {
	    System.out.println("DEBUG:" + getName() + ", t="+now+", mismatch(A) stock numbers: currentStock="+currentStock+ ", getContentAmount()=" + getContentAmount());
	    throw new AssertionError();
	}

	double has =  getContentAmount() + onOrder;

	//	if (Demo.verbose) System.out.println("DEBUG:" + getName() + ", t="+now+", reorderCheck: "+
	//				     "RO:"+reorderPoint + " - ( STOCK:"+currentStock+
	//				     " + OO:" + onOrder + ")");

	if (has > reorderPoint) return;

	final double needed = targetLevel - has;
	double unfilled=needed;

	boolean randomChoice = true; // as opposed to a fraction

	Supplier[] vs = randomChoice?
	    new Supplier[] {pickRandomSupplier(normalSuppliers,getState().random)} :
	    normalSuppliers.toArray(new Supplier[0]);			
	    
	for(Supplier sup: vs) {

	    double w = (randomChoice? 1: sup.fraction);
	    double amt = Math.round(w * needed);
	    double sent = sup.src.feedTo(sup.entryPoint, amt);


	    //	    if (Demo.verbose) System.out.println("DEBUG:" + getName() + ", t="+now+", "+
	    //					 "RO:"+reorderPoint + " - ( STOCK:"+currentStock+
	    //					 " + OO:" + onOrder + "). Requested " + amt + " from " + sup.src +", got " + sent);


	    
	    double backOrderAmt = Math.max(0, amt-sent);
	    if (backOrderAmt>0) {
		if (!(sup.src instanceof Pool)) throw new AssertionError("Non-pools are supposed to 'fill' entire orders");
		((Pool)sup.src).backOrder(sup.entryPoint, backOrderAmt);
	    }
	    
	    unfilled -= sent;
	    orderedToday += sent + backOrderAmt;
	}
	onOrder += orderedToday;
	everOrdered += orderedToday;

	// Some pools, beside making pull orders to upstream pool, also
	// send a "parallel order" to the production unit
	if (parallelSupplier !=null) {
	    double sent = parallelSupplier.src.feedTo(parallelSupplier.entryPoint, needed);	    
	}
    }

    /** Reported in a time series chart file */
    private double orderedToday = 0;

    /** Checks if this pool needs stuff reordered, and makes an order if needed */
    protected void reorderCheck() {
	if (!hasReorderPolicy) return;

	if (mode==ReorderMode.Mode1) {
	    reorderCheck1();
	    return;
	} else {
	    throw new IllegalArgumentException("Pool mode not supported: " + mode);
	}

	/*
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
	if (unfilled > 0 && backOrderSupplier!=null) {
	    ((Pool)backOrderSupplier.src).backOrder(backOrderSupplier.entryPoint, unfilled);
	    onOrder += unfilled;	    
	}

	orderedToday = needed;
	everOrdered += orderedToday;
	*/
    }
    

    public String report() {	
	String s = "[" + getName()+ " has received " + (long)everReceived + " u (not counting initial="+(long)initialReceived+" u), has sent "+(long)everSent+" u";

	if (expiredProductSink != null && expiredProductSink.getEverConsumed() >0) {	
	    s += ". Discarded as expired=" + expiredProductSink.getEverConsumedBatches() +  " ba";
	}
	if (stolenProductSink.getEverConsumed() >0) {
	    s +=  ". Stolen=" + stolenProductSink.getEverConsumedBatches() +  " ba";
	}
	s += ". Available=" + (long)currentStock + " u";
	s += ". Ever ordered "+(long)everOrdered+", expecting receipt of " + (long)onOrder + " u";
	s += ". Still need to send=" + (long)sumNeedToSend() + " u";

	double deficit = everReceived + initialReceived - everSent - currentStock;

	if (deficit!=0) s += ". Deficit=" + deficit;
	
	s += "]";
       return wrap(s);
   }

    /** Used when modeling "depletion" disruptions */
    private MSink stolenProductSink;

    /** Simulates theft or destruction of some of the product stored in 
	this pool
	@param amt The amount of product (units) to destroy.
	@return The amount actually destroyed. It may be smaller
	than requested (because there wasn't this much), or a bit
	larger (becasuse of batch-size rounding)
    */
    protected synchronized double deplete(double amt) {
	double destroyed = 0;
	if (getTypicalProvided() instanceof Batch) {
	    while(destroyed<amt && getAvailable()>0) {
		Batch b=(Batch)entities.getFirst();
		if (!offerReceiver( stolenProductSink, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		entities.remove(b);
		double a = b.getContentAmount();
		destroyed += a;
		currentStock -= a;
	    }
	} else {
	    if (getAvailable()>0) {
		double ga0 = getAvailable();
		offerReceiver(stolenProductSink, amt);
		double a  = (ga0 - getAvailable());
		destroyed += a;
		currentStock -= a;
	    }
	}
	double now = state.schedule.getTime();
	//	if (deficit()!=0) System.out.println("deplete("+amt+") done: t="+now+". "+report());

	return  destroyed;		
    }

    /** Prints the header of the time series CSV file
	@param moreHeaders Names of any additional columns (beyond those that 
	all Pools print) */
    void doChartHeader(String... moreHeaders) {
	String[] a = {"stock","orderedToday"  ,"receivedToday", "stillOnOrder", "demandedToday","sentToday"};
	String[] b = Arrays.copyOf(a, a.length + moreHeaders.length);
	int j=a.length;
	for(String x:  moreHeaders) { b[j++] = x; }

	charter.printHeader(b);
    }

    /** Writes a line to the time series file. Call this from step().
	After the values of the "today's" variables (receivedToday etc)
	have been written out, those variables are reset to 0, for reuse.
     */
    void doChart(double... moreValues) {
	double stock =  getContentAmount();
	double stillOnOrder = onOrder; //everOrdered - everReceived;
	double[] a = {stock,orderedToday,receivedToday,stillOnOrder,demandedToday,sentToday};
	double[] b = Arrays.copyOf(a, a.length + moreValues.length);
	int j=a.length;
	for(double x:  moreValues) { b[j++] = x; }
	
	charter.print(b);
	receivedToday=0;
	demandedToday=0;
	sentToday=0;
	//if (orderedToday!=0) System.out.println("DEBUG: " + getName() +", orderedToday was " + orderedToday);
	orderedToday=0;
	batchesReceivedToday = sumOfAgesReceivedToday = 0;

    }

    /** This is for eeHEP only. If eeHEP is ever made into a separate
	subclass, this method will be moved there.

	Gets a single EE out of this pool, and removes it from the pool.
	@return an EE object if, one can be obtained, or null otherwise
     */
    EE extractOneEE() {
	if (getAvailable()==0) return null;
	Batch b=(Batch)entities.getFirst();
	EE ee;
	if (b instanceof EE) {
	    ee = (EE)b;
	    entities.remove(b);
	} else if (b.getContentAmount()==1) {
	    ee = new EE(b);
	    entities.remove(b);
	} else {
	    ee = new EE(b.split(1));		
	}
	currentStock -= 1;
	everSent+=1;
	double now = state.schedule.getTime();
	// if (deficit()!=0) System.out.println("extract() done: t="+now+". "+report());

	return ee;
    }


    /** Tries to consume (destroy) x unit of the resource stored in this pool.
	@return the amount actually consumed
     */
    double consumeSome(double x) {
	if (getAvailable()==0) return 0;
	double z = 0;
	while(x>0) {
	    Batch b=(Batch)entities.getFirst();
	    double q  = b.getContentAmount();
	    if (q<=x) {
		entities.remove(b);
		z += q;
		x -= q;
	    } else  {
		b.getContent().decrease(x); 
		z += x;
		x = 0;		
	    }
	}
	currentStock -= z;
	everSent+=z;
	//double now = state.schedule.getTime();
	// if (deficit()!=0) System.out.println("extract() done: t="+now+". "+report());

	return z;
    }
    
}

