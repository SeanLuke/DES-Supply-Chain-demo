package  edu.rutgers.sc2;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;


/** Storing a raw material (fungible CountableResource) for a
    production unit.  The buffer may be configured (via the config
    file) to be self-replenishable, so that it sends orders to the supplier
    when needed.
 */
class MaterialBuffer extends sim.des.Queue {

    /** A link back to the Production object whose part this InputStore is */
    private Production whose=null;
    
    protected final ParaSet para;

    /** This is non-null if the buffer has been configured to place its own orders with the supplier */
    Reordering reordering=null;
    
    protected final double  initial;
    public double getInitial() {
	return initial;
    }
   
    protected Charter charter;

    /** How much stuff is stored here. The value should be the same as given by
	getContentAmount(), but without scanning the entire buffer */
    protected double currentStock=0;
    
    /** Keeps track of the amount of product that has been discarded because we discovered
	that it was too close to expiration.  */
    //final ExpiredSink expiredProductSink;
  
    /** Simulates theft or destruction (disruption type A4 etc) */
    final Sink stolenDump;
    
    /** Dummy receiver used for the consumption of this
	ingredient as it's used, with metering */
    MSink sink;

    final CountableResource prototype;
    
    /** Used to detect anomalies in the in-flow of resource into this buffer */
    //private SimpleDetector detector = new SimpleDetector();

    public double getEverReceived() {
	return everReceived;
    }

    /** The amount ever received by the pool (not including the amount
	"received" during the initialization process in the
	constructor)
     */
    double everReceived = 0;

    /** The amount of stuff received since the most recent daily
	chart-writing.  This is incremented at every accept, and
	reduced to 0 at the chart-writing step.
    */
    protected double receivedToday=0;

    /** Used in computing the average age of batches coming in on a given day */
    protected double batchesReceivedToday=0, sumOfAgesReceivedToday=0;
    



    /** Creates the input buffer for one of the products consumed by 
	a Production unit.
	@param _whose The Production unit whose buffer this is
	@param resource The resource stored in this buffer. It can be Batch of CountableResource
	@param _batchSize The standard batch size for consumption of this resource.
     */
    MaterialBuffer(Production _whose, 
	       SimState _state,
	       Config config,
	       CountableResource resource//,
	       //double _batchSize
	       , String[] moreHeaders
	       ) throws IllegalInputException, IOException {
	super(_state, resource);
	whose = _whose;
	prototype = resource;
	//batchSize = _batchSize;
	
	String name = "Input("+getUnderlyingName() +")";
	setName(name);
	
	setOffersImmediately(false); // shipping is driven by pulls from the downstream consumer

	para = config.get(getUnderlyingName());
	if (para==null) throw new  IllegalInputException("No config parameters specified for pool named '" + name +"'");

	initial = para.getDouble("initial");
	//	batchSize = para.getDouble("batch");
	initSupply(initial);
	everReceived = 0; // not counting the initial supply

	reordering = Reordering.mkReordering(this, state, para);
	
	//expiredProductSink = (resource instanceof Batch) ?	    new ExpiredSink(state,  (Batch)resource, 365) : null;

	charter=new Charter(state.schedule, this);
	doChartHeader(moreHeaders);

	//	expiredProductSink = (resource instanceof Batch) ?
	//	    new ExpiredSink(state,  (Batch)resource, 0) : null;
	
	stolenDump = new Sink(state, resource);
	
	sink = new MSink(state, getTypical());
	// this is just for the purpose of the graphical display
	addReceiver(sink);
	//if (expiredProductSink!=null) addReceiver(expiredProductSink);


	//-- we schedule it so that it would check for replenishment daily
	state.schedule.scheduleRepeating(this);

    }

      /** Instantly loads the Queue with the "initial supply", in standard size
	batches made today */
    private void initSupply(double initial) {
	//System.out.println("DEBUG:" + getName() + ", initSupply(" +initial+") in");

	    CountableResource b = new CountableResource((CountableResource)prototype, initial);
	    Provider provider = null;  // why do we need it?
	    if (!accept(provider, b, initial, initial)) throw new AssertionError("Queue did not accept");
	
   	//System.out.println("DEBUG:" + getName() + ", initSupply(" +initial+") out");
    }


    //---

    /** The name of the underlying resource */
    String getUnderlyingName() {
	return //(prototype instanceof Batch)? ((Batch)prototype).getUnderlyingName():
	    prototype.getName();
    }

    double stolen=0;
    int stolenBatches=0;

    /** Removes a batch of stored input resource, to indicate that
	it has been consumed to produce something else. If necessary
	and possible, falls back on the safety stock.
	
	This method should only called if hasEnough() has returned
	true for all ingredients, because we don't want to consume
	one ingredient without being able to consume all other
	ingredients!
	
	@return the consumed batch (so that its data can be used
	for later analysis) if Batch product, or null if fungible

	@throws AssertionError If called when product is not availabile in this
	buffer.

	@return the actually consumed amount
	
    */
    double consumeOneBatch(double batchSize) {
	
	    double a1 = Math.min( getAvailable(), batchSize);
	    double a2 =  batchSize - a1;

	    if (a1>0) {
		boolean z = provide(sink, a1);
		if (!z) throw new IllegalArgumentException("Broken sink? Accept() fails!");
		currentStock -= batchSize;

		if (sink.getLastConsumed() != a1) {
		    String msg = "Batch size mismatch on " + sink +": have " + sink.getLastConsumed()+", expected " + batchSize;
		    throw new IllegalArgumentException(msg);
		}
		return a1;
	    } else {
		return 0;
	    }	
	
    }
	
    /** Do we have enough input materials of this kind to make a batch? 
	While checking the amount, this method also discards expired lots.
	If there is not enough material in the InputStore itself, but 
	a SafetyStock is available, check on that as well.

	FIXME: Here we have a simplifying assumption that all batches are same size. This will be wrong if the odd lots are allowed.
    */
    boolean hasEnough(double inBatchSize) {
	double spare = getAvailable() -  inBatchSize;
	return spare>=0;
    }


    /** Simulates theft or destruction of some of the product stored in 
	this input buffer.
	@param The amount of product (units) to destroy.
	@param return The amount actually destroyed
    */
    synchronized double deplete(double amt) {
	double destroyed = 0;
	if (getAvailable()>0) {
	    double ga0 = getAvailable();
	    offerReceiver(stolenDump, amt);
	    destroyed = ga0 - getAvailable();
	}
	stolen += destroyed;
	stolenBatches ++;
	return  destroyed;		
    }
	
    /** Performs certain auxiliary operation piggy-backed on acceptance of a shipment
     */
    /*
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	//	    String given = (amount instanceof CountableResource)? ""+  amount.getAmount()+" units":		(amount instanceof Batch)? "a batch of " + ((Batch)amount).getContentAmount() +" units":		"an entity";

	double a = (amount instanceof Batch)? ((Batch)amount).getContentAmount() : amount.getAmount();

	boolean z = super.accept(provider,  amount, atLeast,  atMost);
	if (!z) throw new AssertionError();
	currentStock += a;
	everReceived  += a;
	
	// See if the production system is empty, and needs to be "primed"
	// to start.
	if (whose.needProd.getAvailable()==0 && whose.prodDelay.getSize()==0) {
	    double t = state.schedule.getTime();
	    //System.out.println("At " + t + ", the "+getName()+" tries to prime " + whose.getName());
	    
	    // This will "prime the system" by starting the first
	    // mkBatch(), if needed and possible. After that, the
	    // production cycle will repeat via the slackProvider
	    // mechanism
	    whose.mkBatch(//getState()
              );
	}

	return z;
    }
	*/
	
    /** How much stuff is stored by this pool? 
	@return the total content of the pool (in units)
    */
    public double getContentAmount()        {
	if (resource != null) {
	    return resource.getAmount();
	} else if (entities != null) {
	    /*
	      double sum = 0;
	      for(Entity e: entities) {
	      sum +=  (e instanceof Batch)? ((Batch)e).getContentAmount() : e.getAmount();
	      }
	      if (sum!=currentStock) throw new AssertionError("currentStock=" + currentStock +", sum="+sum);
	    */
	    return currentStock;
	    
	}  else {
	    throw new AssertionError();
	    //return 0;
	}
    }

	
    String reportAvailable()  {
	String s = "(" +getAvailable();
	//	if (safety!=null) s += "; S=" + safety.getAvailable();
	s += ")";
	return s;
    }

    String report(boolean showBatchSize)  {
	Vector<String> v= new Vector<>();
	v.add(  getTypical().getName() +":" +
	    (getTypical() instanceof Batch? 
	     (long)getAvailable() + " ba" :
	     getAvailable() + " u" ));

	//	if (expiredProductSink!=null) v.add( expiredProductSink.reportShort());
	if (stolen>0) v.add("(Stolen=" + stolen+ " u = " + stolenBatches + " ba)");
	String s = Util.joinNonBlank(". ", v);
	// if (safety!=null) s += ". " + safety.report();			      
	return s;
    }


    /** The time when the current anomaly was first detected, or null
	if there is no anomaly now. This var is set daily by  detectAnomaly.
     */
    //    private Double anomalySince=null;
    

    /** Have we had a resource inflow anomaly during the precedingh 24 hrs period? */
    /*
    boolean detectAnomaly() {
	double t = state.schedule.getTime();
	boolean anomalyToday=detector.test(t, everReceived, false);
	if (anomalyToday) {
	    if (anomalySince==null) anomalySince=t;
	} else {
	    anomalySince = null;
	}
	return  anomalyToday;
    }
    */

    //---- the replenishment part ----

      /** Performs a reorder, if required; then fills any outstanding back orders, and prints the chart entry. */
    public void step(sim.engine.SimState state) {

	double now = getState().schedule.getTime();
	//System.out.println("DEBUG:" + getName() + ", t="+now+", step");
	
	if (reordering!=null) reordering.reorderCheck();
	doChart(new double[0]);
    }

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

	if (reordering!=null) reordering.onAccept(provider,a);

	everReceived += a;
	receivedToday += a;
	
	currentStock += a;

	if (amount instanceof Batch) {
	    double now = state.schedule.getTime();
	    batchesReceivedToday ++;
	    sumOfAgesReceivedToday += (now - ((Batch)amount).getLot().getEarliestAncestorManufacturingDate());
	}

	//	System.out.println("DEBUG:" + getName() + ", everReceived=" +everReceived +", receivedToday=" + receivedToday + ", currentStock=" + currentStock);

	
	return z;
    }

   /** Prints the header of the time series CSV file
	@param moreHeaders Names of any additional columns (beyond those that 
	all Pools print) */
    void doChartHeader(String... moreHeaders) {
	String[] a = {"stock","orderedToday"  ,"receivedToday", "onOrder"
	    //, "demandedToday","sentToday"
	};
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
	
	double[] a = {stock,
		      (reordering==null? 0: reordering.orderedToday),
		      receivedToday,
		      (reordering==null? 0: reordering.onOrder)
	    //,demandedToday,sentToday
	};
	double[] b = Arrays.copyOf(a, a.length + moreValues.length);
	int j=a.length;
	for(double x:  moreValues) { b[j++] = x; }
	
	charter.print(b);
	receivedToday=0;
	//demandedToday=0;
	//sentToday=0;
	if (reordering!=null) reordering.onChart();
	batchesReceivedToday = sumOfAgesReceivedToday = 0;

    }
    
    
}

