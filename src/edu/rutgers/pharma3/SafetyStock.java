package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models the "safety stock" of some input resource at a Production 
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
 */
public class SafetyStock extends Pool  {

    /** Creates the name for the SafetyStock object. This name will
	be used to look up its properties in the config file. */
    private static String mkName( Production production,  Resource resource) {
	return production.getName() + ".safety."+ Batch.getUnderlyingName(resource);
    }

    /** Creates the SafetyStock for the specified resource at the specified
	production unit.
	@return the new SafetyStock, or null if the config file has no 
	parameters for this (production unit, resource) pair.
    */
    static SafetyStock mkSafetyStock(SimState state, Production production,
			      Config config,  Resource resource)
	throws IllegalInputException, IOException {
	String name = mkName(production, resource);
	ParaSet para = config.get(name);
	if (para==null) return null;
	return new   SafetyStock( state, name, config, resource);
    }
   

    /** Used for replenishing the safety stock from the magic supplier */
    private Delay refillDelay = null;

    /** Are we allowed to access this safety stock only when a
	supply-flowe anomaly has been detected? If non-null, this
	safety stock can only be used if the associated InputStore has
	currently detected an anomaly. The double value indicates the
	actuation delay (i.e. the anomaly must have been in effect for
	so many days before the safety stock can be accessed.
    */
    final Double needsAnomaly;
    
    SafetyStock(SimState state, String name, Config config,
	 Resource resource) throws IllegalInputException, IOException {
	super(state,  name, config, resource, new String[0]);
	String un= Batch.getUnderlyingName(resource);

	if (para.get("delay")==null) throw new IllegalInputException("No " + getName() +",delay value in the config file");
	AbstractDistribution refillDistr = para.getDistribution("delay", state.random); 
	refillDelay = new Delay(state,prototype);
	refillDelay.setDelayDistribution(refillDistr);
	refillDelay.addReceiver(this);
	// to ensure multi-batch shipments are consolidasted safely
	refillDelay.setDropsResourcesBeforeUpdate(false);

	Double q = para.getDouble("needsAnomaly", null);
	needsAnomaly = (q==null || q<0) ? null: q;


      // Initialize the safety stock
	magicFeed(this, initial);
	everReceived = 0; // not counting the initial supply

	
    }
  
    protected void reorderCheck() {
	double need = reorderPoint - (currentStock + onOrder);
	if (need <= 0) return;
	sentToday = magicFeed(refillDelay, need);
	onOrder += sentToday;
    }

    
    /** Has the magic source feed some stuff to the specified
	receiver. 
	@param rcv Where to put stuff. It can be the SafetyStock
	itself (for initialization) or its refillDelay (for a later
	replenishment). In the latter case, ensure that all batches
	travel together.
	@param amt The desired amount of stuff (units) to be sent.
	@return How much stuff (units) has actually be sent. It can exceed amt, due to the last-batch rounding.
    */
    private double magicFeed(Receiver rcv, double amt) {
	double sent = 0;
	Provider provider = null;  // why do we need it?	    
	if (prototype instanceof Batch) {
	    double now = getState().schedule.getTime();
	    Delay delay = (rcv instanceof Delay)? (Delay)rcv: null;
	    	    
	    for(int n=0; sent < amt; n++) {
		Batch b = ((Batch)prototype).mkNewLot(batchSize, now);
		if (!rcv.accept(provider, b, 1, 1)) throw new AssertionError("Queue did not accept");
		if (n==1 && delay!=null) delay.setUsesLastDelay(true);
		sent += b.getContentAmount();
	    }
	    if (delay!=null) delay.setUsesLastDelay(false);
	} else {
	    CountableResource b = new CountableResource((CountableResource)prototype, amt);
	    if (!rcv.accept(provider, b, amt, amt)) throw new AssertionError("Queue did not accept");
	    sent += amt;
	}
	if (Demo.verbose) System.out.println(getName() + " magicFeed gives " + sent);
	return sent;
    }

    /** Checks if the safety stock has the desired amount of product */
    boolean hasEnough(double inBatchSize) {
	if (getTypical() instanceof Batch) {
	    double expiredAmt[]={0};
	    double t = state.schedule.getTime();

	    Batch b = expiredProductSink.getNonExpiredBatch(this, entities, expiredAmt);
	    currentStock -= expiredAmt[0];
	    if (b==null) return false;
	    if (b.getContentAmount()!=inBatchSize) throw new IllegalArgumentException("Unexpected batch size in " + getName() + ": wanted " + inBatchSize +", found " + b.getContentAmount());
	    return true;
	} else if (getTypical()  instanceof CountableResource) {
		double ava = getAvailable();
		return (ava >= inBatchSize);
	} else throw new IllegalArgumentException("Wrong input resource type; getTypical()="  +getTypical());
    }

    private Batch getFirst() {
	return (Batch)entities.getFirst();
    }

    private boolean remove(Batch b) {
	return entities.remove(b);
    }
  

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
    
    Batch consumeOneBatch(MSink sink, double inBatchSize) {
	Batch b = null;
	double sent=0;
	if (getTypical() instanceof Batch) {
	    b=getFirst();
	    if (b.getContentAmount() !=  inBatchSize)  throw new IllegalArgumentException("Unexpected batch size in " + getName() + ": wanted " + inBatchSize +", found " + b.getContentAmount());
	    if (!offerReceiver(sink, b)) throw new AssertionError("Sinks ought not to refuse stuff!");
	    remove(b);	    
	} else if (getTypical() instanceof CountableResource) {
	    if (getAvailable()<inBatchSize)  throw new IllegalArgumentException(getName() + ".consumeOneBatch(): have="+getAvailable()+", need=" +  (long)inBatchSize);
	    boolean z = provide(sink, inBatchSize);
	    if (!z) throw new AssertionError("Sinks ought not to refuse stuff!");
	    if (sink.lastConsumed != inBatchSize) {
		String msg = "Batch size mismatch on " + sink +": have " + (long)sink.lastConsumed+", expected " + (long)inBatchSize;
		throw new IllegalArgumentException(msg);
	    }		
	} else throw new IllegalArgumentException("Wrong input resource type");
	currentStock -= batchSize;   
	everSent += batchSize;
	sentToday += batchSize;
	return b;	    
    }

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

}
