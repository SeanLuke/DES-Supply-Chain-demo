package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;


/** This is an auxiliary class for Production. It represents a Queue
    for storing one of the input ingredients used by the Production
    unit. It has a facility for discarding expired lots, as well as a
    facility for destroying "stolen" lots during a disruption.
 */
class InputStore extends sim.des.Queue {

    /** A link back to the Production object whose part this InputStore is */
    final Production whose;


    /** Keeps track of the amount of product that has been discarded because we discovered
	that it was too close to expiration.  */
    final ExpiredSink expiredProductSink;
  
    /** Simulates theft or destruction (disruption type A4 etc) */
    final Sink stolenDump;
    
    /** Dummy receiver used for the consumption of this
	ingredient as it's used, with metering */
    MSink sink;

    /** Either Batch (in most cases) or CountableResource (for pac.mat) */
    final Resource prototype;
    
    /** The standard batch size for this input */
    final double batchSize;

    /** How much stuff is stored here. The value should be the same as given by
	getContentAmount(), but without scanning the entire buffer */
    private double currentStock=0;
    double everReceived=0;

    /** The safety stock for this input store. May be zero if not provided
	for in the config file */
    SafetyStock safety = null;

    /** Used to detect anomalies in the in-flow of resource into this buffer */
    private SimpleDetector detector = new SimpleDetector();


    /** Creates the input buffer for one of the products consumed by 
	a Production unit.
	@param _whose The Production unit whose buffer this is
	@param resource The resource stored in this buffer. It can be Batch of CountableResource
	@param _batchSize The standard batch size for consumption of this resource.
     */
    InputStore(Production _whose, 
	       SimState _state,
	       Config config,
	       Resource resource,
	       double _batchSize) throws IllegalInputException, IOException {
	super(_state, resource);
	whose = _whose;
	prototype = resource;
	batchSize = _batchSize;
	
	String name = "Input("+getUnderlyingName() +")";
	setName(name);
	
	setOffersImmediately(false); // the stuff sits here until taken
	
	expiredProductSink = (resource instanceof Batch) ?
	    new ExpiredSink(state,  (Batch)resource, 0) : null;
	
	stolenDump = new Sink(state, resource);
	
	sink = new MSink(state, getTypical());
	// this is just for the purpose of the graphical display
	addReceiver(sink);
	if (expiredProductSink!=null) addReceiver(expiredProductSink);
	safety = SafetyStock.mkSafetyStock( state, whose,
					    config,  resource);

    }

    /** The name of the underlying resource */
    String getUnderlyingName() {
	return (prototype instanceof Batch)? ((Batch)prototype).getUnderlyingName(): prototype.getName();
    }

    double stolen=0;
    int stolenBatches=0;
    
    private Batch getFirst() {
	return (Batch)entities.getFirst();
    }

    private boolean remove(Batch b) {
	return entities.remove(b);
    }
    
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

    */
    Batch consumeOneBatch() {
	
	if (getTypical() instanceof Batch) {
	    //z = p.provide(p.sink, 1);

	    if (getAvailable()>0) {	    
		Batch b=getFirst();			
		if (!offerReceiver(sink, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		remove(b);
		currentStock -= b.getContentAmount();
		return b;
	    } else if (safety!=null) {
		//double sent = safety.feedTo(sink, batchSize);
		//if (sent!=batchSize) {
		//    String msg = "Batch size mismatch on " + sink +": have " + sink.lastConsumed+", expected " + batchSize;
		//    throw new IllegalArgumentException(msg);
		//}
		return safety.consumeOneBatch(sink, batchSize);
	    } else throw new AssertionError("consumeOneBatch() should not be called if the resource is not available");
		
	} else if (getTypical() instanceof CountableResource) {
	     
	    double a1 = Math.min( getAvailable(), batchSize);
	    double a2 =  batchSize - a1;

	    if (a1>0) {
		boolean z = provide(sink, a1);
		if (!z) throw new IllegalArgumentException("Broken sink? Accept() fails!");
		currentStock -= batchSize;

		if (sink.lastConsumed != a1) {
		    String msg = "Batch size mismatch on " + sink +": have " + sink.lastConsumed+", expected " + batchSize;
		    throw new IllegalArgumentException(msg);
		}
	    }
	
	    if (a2==0) 	    return null;
	    else if (safety==null) throw new AssertionError("consumeOneBatch() should not be called if the resource is not available");
	    else return safety.consumeOneBatch(sink, a2);       		
	    
	} else throw new IllegalArgumentException("Wrong input resource type");


	
    }
	
    /** Do we have enough input materials of this kind to make a batch? 
	While checking the amount, this method also discards expired lots.
	If there is not enough material in the InputStore itself, but 
	a SafetyStock is available, check on that as well.

	FIXME: Here we have a simplifying assumption that all batches are same size. This will be wrong if the odd lots are allowed.
    */
    boolean hasEnough(double inBatchSize) {
	if (getTypical() instanceof Batch) {
	    double expiredAmt[]={0};
	    double t = state.schedule.getTime();

	    Batch b = expiredProductSink.getNonExpiredBatch(this, entities, expiredAmt);
	    currentStock -= expiredAmt[0];

	    if (b!=null) return true;
	    return (safety!=null) &&
		safety.accessAllowed(t, anomalySince) &&
		safety.hasEnough(inBatchSize);
	} else if (getTypical()  instanceof CountableResource) {
		double spare = getAvailable() -  inBatchSize;
		return spare>=0 || (safety!=null && safety.hasEnough(-spare));
	} else throw new IllegalArgumentException("Wrong input resource type; getTypical()="  +getTypical());
    }


    /** Simulates theft or destruction of some of the product stored in 
	this input buffer.
	@param The amount of product (units) to destroy.
	@param return The amount actually destroyed
    */
    synchronized double deplete(double amt) {
	double destroyed = 0;
	amt = Math.round(amt); // because offerReceiver does not like fractions
	if (getTypical() instanceof Batch) {
	    while(destroyed<amt && getAvailable()>0) {
		Batch b=getFirst();
		if (!offerReceiver( stolenDump, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		remove(b);
		double a = b.getContentAmount();
		currentStock -= a;
		destroyed += a;
		stolenBatches ++;
	    }
	} else {
	    if (getAvailable()>0) {
		double ga0 = getAvailable();
		offerReceiver(stolenDump, amt);
		destroyed = ga0 - getAvailable();
	    }
	}
	stolen += destroyed;
	return  destroyed;		
    }
	
    /** Performs certain auxiliary operation piggy-backed on acceptance
     */
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
	if (safety!=null) s += "; S=" + safety.getAvailable();
	s += ")";
	return s;
    }

    String report(boolean showBatchSize)  {
	Vector<String> v= new Vector<>();
	v.add(  getTypical().getName() +":" +
	    (getTypical() instanceof Batch? 
	     (long)getAvailable() + " ba" :
	     getAvailable() + " u" ));

	if (expiredProductSink!=null) v.add( expiredProductSink.reportShort());
	if (stolen>0) v.add("(Stolen=" + stolen+ " u = " + stolenBatches + " ba)");
	String s = Util.joinNonBlank(". ", v);
	if (safety!=null) s += ". " + safety.report();			      
	return s;
    }


    /** The time when the current anomaly was first detected, or null
	if there is no anomaly now. This var is set daily by  detectAnomaly.
     */
    private Double anomalySince=null;
    

    /** Have we had a resource inflow anomaly during the precedingh 24 hrs period? */
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
   

}

