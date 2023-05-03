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

/** SC-2: The "safety stock" is merely an alternative ordering (MTS-based)
    mechanism for a particular input buffer. 

    <p>SC-1:
   Models the "safety stock" of some input resource at a Production 
    node. The "safety stock" can be used when the InputStore for the
    resource in question is empty. The safety stock is replenished
    from an inexhaustible and un-disturbable "magic source", so it's
    almost always available. 

    <p>The parameters for, e.g. the RawMaterial safety stock in ApiProduction
    come from the config file lines beginning with
<pre>
ApiProduction.safety.RawMaterial,initial,...
ApiProduction.safety.RawMaterial,reorderPoint,...
</pre>


<p>The "extends Probe" part is here so that we can have the supplier
send resources to the SafetyStock... which will immediately offer them to
the InputStore whom it serves.
 */
public class SafetyStock // extends Pool
extends Probe implements Reporting
{

    /** Creates the name for the SafetyStock object. This name will
	be used to look up its properties in the config file. */
    private static String mkName( Production production,  Resource resource) {
	return production.getName() + ".safety."+ Batch.getUnderlyingName(resource);
    }


    final InputStore whose;
    final ParaSet para;

    /** This can be Batch, or CountableResource. It is similar to
	Provider.typical, but (in the Batch case) it also has information
	about the underlying resource in its storage[]. In the
	Wholesaler Pool etc, this represents batches of packaged
	drug. */
    protected final Resource prototype;

    protected double reorderPoint=0;
    private double targetLevel=0;
    private double initial=0;

        /** The outstanding order amount: the stuff that this pool has ordered, but which has not arrived yet.  It is used so that the pool does not try to repeat its order daily until the orignal order arrives.
      FIXME: it would be better to have separate vars for separate suppliers
 */
    protected final OnOrder onOrder;


    double everOrdered = 0, everReceived=0;
    
    /** Reported in a time series chart file */
    double orderedToday = 0, receivedToday=0;

    private Timed haltedUntil = new Timed();    

    /** Checks if there is a safety-stock disruption in effect, which
	stops the SS level check until a certain point.
    */
    boolean isHalted(double now) {
	return haltedUntil.isOn( now );
    }

    /** Disables the safety stock level-checking until the specified
	time point. This implements one of the disruption types in SC-2. */
    void haltUntil(double t) {
	haltedUntil.enableUntil( t );
    }

    
    /** Creates the SafetyStock for the specified resource at the specified
	production unit.
	@param whose The InputStore with whom this SafetyStock is
	associated, and whose supply level it will be monitoring.
	@param production The production unit whose InputStore this SS
	will be monitoring. This parameter is only used to generate
	a suitable name for this SS. 
	@return the new SafetyStock, or null if the config file has no	
	parameters for this (production unit, resource) pair.
    */
    static SafetyStock mkSafetyStock(SimState state, Production production,
				     InputStore whose,
				     Config config,  Resource resource)
	throws IllegalInputException, IOException {
	String name = mkName(production, resource);
	ParaSet para = config.get(name);
	if (para==null) return null;
	return new   SafetyStock( state, name, whose, config, resource);
    }
   

    /** Used for replenishing the safety stock from the magic supplier.
	The null value means "no delay".
     */
    private Delay refillDelay = null;

    /** Are we allowed to access this safety stock only when a
	supply-flow anomaly has been detected? If non-null, this
	safety stock can only be used if the associated InputStore has
	currently detected an anomaly. The double value indicates the
	actuation delay (i.e. the anomaly must have been in effect for
	so many days before the safety stock can be accessed.
	(This was used in SC-1, but apparently won't be used in SC-2).
    */
    final Double needsAnomaly;
    
    SafetyStock(SimState state, String name,  InputStore _whose, Config config,
	 Resource _resource) throws IllegalInputException, IOException {
	super(state);
	//super(state,  name, config, resource, new String[0]);
	setName(name);
	para = config.get(name);
	prototype = _resource;
       
	whose = _whose;
	addReceiver(whose);
	
	Double r = para.getDouble("reorderPoint", null);

	reorderPoint = r;

	r =  para.getDouble("targetLevel", null);
	if (r==null)  throw new  IllegalInputException("Element named '" + whose.getName() +"' has reorder point, but no target level");
	targetLevel = r;

	initial = para.getDouble("initial", null);
	
	String un= Batch.getUnderlyingName(prototype);

	if (para.get("delay")==null) throw new IllegalInputException("No " + getName() +",delay value in the config file");
	AbstractDistribution refillDistr = para.getDistribution("delay", state.random); 
	refillDelay = new Delay(state,prototype);
	refillDelay.setDelayDistribution(refillDistr);
	refillDelay.setName("RefillDelay." + name);
	refillDelay.addReceiver(this);
	this.addReceiver(whose);
	// to ensure multi-batch shipments are consolidated safely
	if (getTypicalProvided() instanceof Batch) {
	    refillDelay.setDropsResourcesBeforeUpdate(false);
	}

	
	onOrder = new OnOrder( para.getDouble("orderExpiration", Double.POSITIVE_INFINITY ));
	
	Double q = para.getDouble("needsAnomaly", null);
	needsAnomaly = (q==null || q<0) ? null: q;

	magicSource = new MagicSource(state);
	magicChannel = new Channel(magicSource, refillDelay, getName());
	initSupply(initial);
    }

    private final MagicSource magicSource;
    
    /** This is just a dummy variable for use in the Order constructor */
    private final Channel magicChannel;
    
    /** This methos is called daily, from step(), to check the
	current stock level and make a replenishment order,
	if needed. */
    protected void reorderCheck() {

	double now = getState().schedule.getTime();
	if (isHalted(now)) return; // disruption

	
	//	if (Demo.verbose && currentStock != getContentAmount()) {
	//	    System.out.println("DEBUG:" + getName() + ", t="+now+", mismatch(A) stock numbers: currentStock="+currentStock+ ", getContentAmount()=" + getContentAmount());
	//	}

	Vector<Order> eo = onOrder.refresh(now);
	if (!Demo.quiet && eo.size()>0)  System.out.println(getName() + ", t="+now+", expired orders: " + Util.joinNonBlank(", ", eo));

	double has =  whose.getContentAmount() + onOrder.sum();
	double deficit = reorderPoint - has;

	//	if (Demo.verbose) System.out.println("DEBUG:" + getName() + ", t="+now+", reorderCheck: "+
	//			   "RO:"+reorderPoint + " - ( STOCK:"+currentStock+
	//			   " + OO:" + onOrder + ")=deficit=" + deficit + ". Delay=" +  refillDelay.report());

	if (deficit <= 0) return;

	double need = targetLevel - has;
	Order order = new Order(now, magicChannel, orderedToday);

	magicSource.request(order);
	orderedToday = need; 
	
	onOrder.add(order);
	everOrdered += orderedToday;


    }

    class MagicSource extends Source implements BatchProvider2 {

	MagicSource(SimState state) {
	    super(state, prototype);
	    setName( SafetyStock.this.getName() + ".magicSource");
	}
    
	/** Has the built-in magic source feed some stuff to the specified
	    receiver. 
	    @param rcv Where to put stuff. It can be the SafetyStock
	    itself (for initialization) or its refillDelay (for a later
	    replenishment). In the latter case, ensure that all batches
	    travel together.
	    @param amt The desired amount of stuff (units) to be sent.
	    @return How much stuff (units) has actually be sent. It can exceed amt, due to the last-batch rounding.
	*/
	public void request(Order order) {
	    Receiver rcv = order.channel.receiver;
	    double amt = order.amount;

	    double sent = 0;
	    if (prototype instanceof Batch) {
		double now = getState().schedule.getTime();
		Delay delay = (rcv instanceof Delay)? (Delay)rcv: null;
		
		for(int n=0; sent < amt; n++) {
		    //Batch b = ((Batch)prototype).mkNewLot(batchSize, now);
		    Batch b = ((Batch)prototype).mkNewLot(amt, now);
		    if (!rcv.accept(this, b, 1, 1)) throw new AssertionError("Queue did not accept");
		if (n==1 && delay!=null) delay.setUsesLastDelay(true);
		sent += b.getContentAmount();
		}
		if (delay!=null) delay.setUsesLastDelay(false);
	    } else {
		amt = Math.ceil(amt); // ensure that the value is integer, as needed for CR
		
		CountableResource b = new CountableResource((CountableResource)prototype, amt);
		
		//System.out.println("DEBUG:" + getName() + ", magicFeed(" +amt+") to " +rcv + "; sending " + b.getAmount());
		
	    
		if (!rcv.accept(this, b, amt, amt)) throw new AssertionError("Queue did not accept");
		sent += amt;
		
	    }
	    //if (Demo.verbose)
	    //	System.out.println("DEBUG:" + getName() + " magicFeed gives " + sent);
	    //return sent;
	}

	/** Not used */
	public void registerChannel(Channel channel) {}
    }

    /** Checks if the safety stock has the desired amount of product */
    /*
    boolean hasEnough(double inBatchSize) {
	if (getTypicalProvided() instanceof Batch) {
	    double expiredAmt[]={0};
	    double t = state.schedule.getTime();

	    Batch b = expiredProductSink.getNonExpiredBatch(this, entities, expiredAmt);
	    currentStock -= expiredAmt[0];
	    if (Demo.verbose && currentStock != getContentAmount()) {
		System.out.println("DEBUG:" + getName() + ", t="+t+", mismatch (E) stock numbers: currentStock="+currentStock+ ", getContentAmount()=" + getContentAmount());
	    }
	    if (b==null) return false;
	    if (b.getContentAmount()!=inBatchSize) throw new IllegalArgumentException("Unexpected batch size in " + getName() + ": wanted " + inBatchSize +", found " + b.getContentAmount());
	    return true;
	} else if (getTypicalProvided()  instanceof CountableResource) {
		double ava = getAvailable();
		return (ava >= inBatchSize);
	} else throw new IllegalArgumentException("Wrong input resource type; getTypicalProvided()="  +getTypicalProvided());
    }

    private Batch getFirst() {
	return (Batch)entities.getFirst();
    }

    private boolean remove(Batch b) {
	return entities.remove(b);
    }
    */

    /** Removes a batch of input resource stored in this safety stock,
	to indicate that it has been consumed to produce something
	else.
	
	This method should only called if hasEnough() has returned
	true for all ingredients, because we don't want to consume
	one ingredient without being able to consume all other
	ingredients!

	@param sink The sink of the InputStore
	
	@return the consumed batch (so that its data can be used
	for later analysis) if Batch product, or null if fungible
    */
    /*
    Batch consumeOneBatch(MSink sink, double inBatchSize) {
	Batch b = null;
	double sent=0;
	if (getTypicalProvided() instanceof Batch) {
	    b=getFirst();
	    if (b.getContentAmount() !=  inBatchSize)  throw new IllegalArgumentException("Unexpected batch size in " + getName() + ": wanted " + inBatchSize +", found " + b.getContentAmount());
	    if (!offerReceiver(sink, b)) throw new AssertionError("Sinks ought not to refuse stuff!");
	    remove(b);	    
	} else if (getTypicalProvided() instanceof CountableResource) {
	    if (getAvailable()<inBatchSize)  throw new IllegalArgumentException(getName() + ".consumeOneBatch(): have="+getAvailable()+", need=" +  (long)inBatchSize);
	    boolean z = provide(sink, inBatchSize);
	    if (!z) throw new AssertionError("Sinks ought not to refuse stuff!");
	    if (sink.getLastConsumed() != inBatchSize) {
		String msg = "Batch size mismatch on " + sink +": have " + (long)sink.getLastConsumed()+", expected " + (long)inBatchSize;
		throw new IllegalArgumentException(msg);
	    }		
	} else throw new IllegalArgumentException("Wrong input resource type");
	currentStock -= inBatchSize;
	everSent += inBatchSize;
	sentToday += inBatchSize;
	return b;	    
    }
    */
    
    /** Are we allowed to access this safety stock by the "anomaly since"
	policy, if it exists?
	@param anomalyStart Null if there is no anomaly has been presently
	detected, or the time when the current anomaly was first detected.
	@return true if there is no restriction, or if the anomaly has been 
	in effect for a sufficiently long time
    */  
    boolean accessAllowed(double now, Double anomalyStart) {
	return (needsAnomaly==null) ||
	    (anomalyStart!=null  && anomalyStart <= now - needsAnomaly);
    }


    /** This is called from a supplier (or the associated Delay) when
	a batched shipped to this pool arrives.

	Every piece of resource getting into this pool goes through this method;
	this is why we have currentStock increment done in here.
	@param amount a Batch object
     */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {

	double a = Batch.getContentAmount(amount);


	//System.out.println("DEBUG:" + getName() + ", stock=" +whose.getContentAmount() +", accepting " +amount + " from provider=" + provider + ". The destination stores " + whose.prototype);


	//-- Had to make a fix in Filter.java for this to work, as per DES Qu. No. 36
	boolean z = super.accept(provider, amount, atLeast, atMost);
	//whose.accept(provider, amount, atLeast, atMost);

	
	if (!z) throw new AssertionError("Pool " + getName() + " refused delivery. This ought not to happen!");
	if ((amount instanceof CountableResource) && amount.getAmount()>0) throw new AssertionError("Incomplete acceptance by a Pool. Our pools ought not to do that!");
	double now = getState().schedule.getTime();
	//if (provider instanceof SimpleDelay) { // Received a non-immediate delivery
	double late = onOrder.subtract(now, a);

	if (late>0) {
	    //System.out.println("DEBUG: "+getName()+", at=" + now+", just processed a late shipment of " + late);
	}

	//if (onOrder < 0) { // they have over-delivered
	//  onOrder=0;
	//}
	everReceived += a;
	receivedToday += a;
	
	//currentStock += a;

	//	if (amount instanceof Batch) {
	//	    double now = state.schedule.getTime();
	//	    batchesReceivedToday ++;
	//	    sumOfAgesReceivedToday += (now - ((Batch)amount).getLot().getEarliestAncestorManufacturingDate());
	//	}

	//	System.out.println("DEBUG:" + getName() + ", everReceived=" +everReceived +", receivedToday=" + receivedToday + ", currentStock=" + currentStock);

	
	return z;
    }


    public String report() {


	String ba = whose.getTypicalProvided() instanceof Entity? " ba": " u";
	
	String s = "[" + getName()+
	    " has ordered " + (long)everOrdered + " u, " +
	    " has received " + (long)everReceived + " u. " +
	    "On order=" + onOrder +"; in transit " + refillDelay.getDelayedPlusAvailable() + ba;

	s += "]";
       return wrap(s);
   }


    
    public void step(sim.engine.SimState state) {


	//System.out.println("DEBUG:" + getName() + ", t="+now+", step");


	
	reorderCheck();
	//	fillBackOrders();
	//	doChart(new double[0]);
    }
    

    /** Instantly loads the Queue with the "initial supply", in standard size
	batches made today */
    private void initSupply(double initial) {
	if (initial<=0) return;
	//System.out.println("DEBUG:" + getName() + ", initSupply(" +initial+") in");
	if (prototype instanceof Batch) {

	    double batchSize = Math.round(initial);
	    int n = 1; // (int)Math.round( initial / batchSize);
	    double now = state.schedule.getTime();
	    for(int j=0; j<n; j++) {
		Batch whiteHole = ((Batch)prototype).mkNewLot(batchSize, now);
		Provider provider = null;  // why do we need it?
		if (!whose.doAccept(provider, whiteHole, 1, 1, true)) throw new AssertionError("Queue did not accept");
	    }

	} else {
	    CountableResource b = new CountableResource((CountableResource)prototype, initial);
	    Provider provider = null;  // why do we need it?
	    if (!whose.doAccept(provider, b, initial, initial, true)) throw new AssertionError("Queue did not accept");
	}	
   	//System.out.println("DEBUG:" + getName() + ", initSupply(" +initial+") out");
    }


    
}
