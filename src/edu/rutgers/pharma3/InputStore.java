package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
//import sim.des.portrayal.*;

import edu.rutgers.util.*;
//import edu.rutgers.pharma3.Disruptions.Disruption;


/** This is an auxiliary class for Production. It represents a Queue
    for storing an input ingredient. It has a facility for discarding
    expired lots, as well as a facility for destroying "stolen" lots
    during a disruption.
 */
class InputStore extends sim.des.Queue {

    /** A link back to the Production object whose part this InputStore is */
    final Production whose;
    
    /** Used to discard expired lots */
    Sink expiredDump;
    /** Simulates theft or destruction (disruption type A4 etc) */
    Sink stolenDump;
    
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
    
    InputStore(Production _whose, //ParaSet _para,
	       SimState _state,
	       Resource resource,
	       double _batchSize) throws IllegalInputException {
	super(_state, resource);
	whose = _whose;
	prototype = resource;
	batchSize = _batchSize;
	
	String name = "Input("+getUnderlyingName() +")";
	//name = whose.getName() + "/Input store for " + resource.getName();
	setName(name);
	
	setOffersImmediately(false); // the stuff sits here until taken
	
	expiredDump = new Sink(state, resource);
	stolenDump = new Sink(state, resource);
	
	sink = new MSink(state, getTypical());
	// this is just for the purpose of the graphical display
	addReceiver(sink);
	addReceiver(expiredDump);
	
    }

    /** The name of the underlying resource */
    String getUnderlyingName() {
	return (prototype instanceof Batch)? ((Batch)prototype).getUnderlyingName(): prototype.getName();
    }

    double discardedExpired=0;
    int discardedExpiredBatches=0;
    double stolen=0;
    int stolenBatches=0;
    
    private Batch getFirst() {
	return (Batch)entities.getFirst();
    }

    private boolean remove(Batch b) {
	return entities.remove(b);
    }
    
    /** Removes a batch of stored input resource, to indicate that
	it has been consumed to produce something else.
	
	This method should only called if hasEnough() has returned
	true for all ingredients, because we don't want to consume
	one ingredient without being able to consume all other
	ingredients!
	
	@return the consumed batch (so that its data can be used
	for later analysis) if Batch product, or null if fungible
    */
    Batch consumeOneBatch() {
	
	if (getTypical() instanceof Batch) {
	    //z = p.provide(p.sink, 1);
	    Batch b=getFirst();			
	    if (!offerReceiver(sink, b)) throw new AssertionError("Sinks ought not refuse stuff!");
	    remove(b);
	    currentStock -= b.getContentAmount();
	    return b;
	    
	} else if (getTypical() instanceof CountableResource) {
	    boolean z = provide(sink, batchSize);
	    if (!z) throw new IllegalArgumentException("Broken sink? Accept() fails!");
	    currentStock -= batchSize;


	    if (sink.lastConsumed != batchSize) {
		String msg = "Batch size mismatch on " + sink +": have " + sink.lastConsumed+", expected " + batchSize;
		throw new IllegalArgumentException(msg);
	    }

	    return null;
		
	} else throw new IllegalArgumentException("Wrong input resource type");
	    
    }
	
    /** Do we have enough input materials of this kind to make a batch? 
	While checking the amount, this method also discards expired lots.

	FIXME: Here we have a simplifying assumption that all batches are same size. This will be wrong if the odd lots are allowed.
    */
    boolean hasEnough(double inBatchSize) {
	if (getTypical() instanceof Batch) {
	    double t = state.schedule.getTime();
	    
	    // Discard any expired batches
	    Batch b; 
	    while (getAvailable()>0 &&
		   (b=getFirst()).willExpireSoon(t, 0)) {
		
		// System.out.println(getName() + ", has expired batch; created=" + b.getLot().manufacturingDate +", expires at="+b.getLot().expirationDate+"; now=" +t);
		if (!offerReceiver( expiredDump, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		remove(b);
		double a = b.getContentAmount();
		currentStock -= a;
		discardedExpired += a;
		discardedExpiredBatches ++;		    
	    }
		
	    return (getAvailable()>0);
	} else if (getTypical()  instanceof CountableResource) {
	    return getAvailable()>=inBatchSize;
	} else throw new IllegalArgumentException("Wrong input resource type; getTypical()="  +getTypical());
    }


    /** Simulates theft or destruction of some of the product stored in 
	this input buffer.
	@param The amount of product (units) to destroy.
	@param return The amount actually destroyed
    */
    synchronized double deplete(double amt) {
	double destroyed = 0;
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
	
	// See if the production system is empty, and needs to be "primed"
	// to start.
	if (whose.needProd.getAvailable()==0 && whose.prodDelay.getSize()==0) {
	    double t = state.schedule.getTime();
	    //System.out.println("At " + t + ", the "+getName()+" tries to prime " + whose.getName());
	    
	    // This will "prime the system" by starting the first
	    // mkBatch(), if needed and possible. After that, the
	    // production cycle will repeat via the slackProvider
	    // mechanism
	    whose.mkBatch(getState());
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

	
    String report(boolean showBatchSize)  {
	String s = getTypical().getName() +":" +
	    (getTypical() instanceof Batch? 
	     getAvailable() + " ba" :
	     getAvailable() + " u" );
        
	if (discardedExpiredBatches>0) s += ". (Discarded expired=" + discardedExpired + " u = " + discardedExpiredBatches + " ba)";
	if (stolen>0) s += ". (Stolen=" + stolen+ " u = " + stolenBatches + " ba)";
	return s;
    }
	
}

