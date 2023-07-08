package  edu.rutgers.sc3;

import edu.rutgers.supply.*;

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
    //    final double batchSize;

    /** How much stuff is stored here. The value should be the same as given by
	getContentAmount(), but without scanning the entire buffer */
    private double currentStock=0;
    double everReceived=0, everReceivedFromMagic=0;

    /** Used to collect daily statistic */
    double receivedTodayFromMagic = 0, receivedTodayFromNormal = 0;

    /** This is to be called after the day's row in the time series chart file
	has been written out. */
    void clearDailyStats() {
	receivedTodayFromMagic = 0;
	receivedTodayFromNormal = 0;
	if (safety!=null) safety.clearDailyStats();
    }
	
    
    /** The safety stock for this input store. May be null if not provided
	for in the config file */
    SafetyStock safety = null;

    /** Used to detect anomalies in the in-flow of resource into this buffer */
    //private SimpleDetector detector = new SimpleDetector();



    /** MTO orders, if any, are sent to this source. */
    //BatchProvider2 mtoSource=null;
    //Channel mtoChannel=null;
    
    final ParaSet para;
    
    /** Creates the input buffer for one of the products consumed by 
	a Production unit.
	@param _whose The Production unit whose buffer this is
	@param resource The resource stored in this buffer. It can be Batch of CountableResource
	//@param _batchSize The standard batch size for consumption of this resource.
     */
    InputStore(Production _whose, 
	       SimState _state,
	       Config config,
	       Resource resource ) throws IllegalInputException, IOException {
	super(_state, resource);
	whose = _whose;
	prototype = resource;
	//batchSize = _batchSize;
	
	String name = mkName(whose, resource);
	setName(name);


	//System.out.println("DEBUG: InputStore(r=" + resource+")");
	//System.out.println("DEBUG: r.name=" + resource.getName());
	
	setOffersImmediately(false); // the stuff sits here until taken
	
	expiredProductSink = (resource instanceof Batch) ?
	    new ExpiredSink(state,  (Batch)resource, 0) : null;
	
	stolenDump = new Sink(state, resource);
	
	sink = new MSink(state, getTypicalProvided());
	// this is just for the purpose of the graphical display
	addReceiver(sink);
	if (expiredProductSink!=null) addReceiver(expiredProductSink);
	safety = SafetyStock.mkSafetyStock( state, whose, this,
					    config,  resource);
	//-- we schedule it so that it would check for replenishment daily
	state.schedule.scheduleRepeating(safety);

	// Optionally, this may be a pool capable of sending replenishement requests
	para = config.get(name);

    }


   /** Creates the name for the InputStore object. This name will
	be used to look up its properties in the config file. */
    private static String mkName( Production production,  Resource resource) {
	return production.getName() + ".input."+ Batch.getUnderlyingName(resource);
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

	@param batchSize How much to consume
    */
    Batch consumeOneBatch(final double batchSize) {
	if (batchSize==0) return null;
	if (getTypicalProvided() instanceof Batch) {
	    //z = p.provide(p.sink, 1);

	    double needed=batchSize, consumed = 0;
	    Batch newBatch = null;
	    
	    while(needed>0 && getAvailable()>0) {	    
		Batch b=getFirst();			

		double found =  b.getContentAmount();
		double x = Math.min(found, needed);
		if (found<=needed) {
		    if (newBatch==null) newBatch=b;
		    else newBatch.merge(b);		    
		    remove(b);
		} else {
		    if (newBatch==null) newBatch=b.split(needed);
		    else newBatch.merge( b.split(needed));
		}
		needed -= x;
		consumed += x;
	    }

	    if (needed > 0) throw new IllegalArgumentException("Could not consume " + batchSize + " units from " + getName() +", because it only had " + consumed);
	    currentStock -= consumed;
	    if (newBatch.getContentAmount() != consumed) throw new AssertionError();
	    if (!offerReceiver(sink, newBatch)) throw new AssertionError("Sinks ought not refuse stuff!");

	    return newBatch;
		
	} else if (getTypicalProvided() instanceof CountableResource) {
	     
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
	    }
	
	    if (a2==0) 	    return null;
	    else //if (safety==null)
		throw new AssertionError("consumeOneBatch() should not be called if the resource is not available");
	    //else return safety.consumeOneBatch(sink, a2);       		
	    
	} else throw new IllegalArgumentException("Wrong input resource type");


	
    }
	
    /** Do we have enough input materials of this kind to make a batch? 
	While checking the amount, this method also discards expired lots.
	If there is not enough material in the InputStore itself, but 
	a SafetyStock is available, check on that as well.

	FIXME: Here we have a simplifying assumption that all batches are same size. This will be wrong if the odd lots are allowed.

	@param inBatchSize The needed amount. Sometimes can be zero (as in shared input buffers, where some recipes have 0 amount)
    */
    boolean hasEnough(double inBatchSize) {
	if (inBatchSize==0) return true; 
	if (getTypicalProvided() instanceof Batch) {
	    double expiredAmt[]={0};
	    double t = state.schedule.getTime();

	    boolean has = expiredProductSink.hasEnoughNonExpired(this, entities, expiredAmt, inBatchSize);
	    currentStock -= expiredAmt[0];
	    return has;
	} else if (getTypicalProvided()  instanceof CountableResource) {
	    double spare = getAvailable() -  inBatchSize;
	    return spare>=0;// || (safety!=null && safety.hasEnough(-spare));
	} else throw new IllegalArgumentException("Wrong input resource type; getTypicalProvided()="  +getTypicalProvided());
    }


    /** Simulates theft or destruction of some of the product stored in 
	this input buffer.
	@param The amount of product (units) to destroy.
	@param return The amount actually destroyed
    */
    synchronized double deplete(double amt) {
	double destroyed = 0;
	amt = Math.round(amt); // because offerReceiver does not like fractions
	if (getTypicalProvided() instanceof Batch) {
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

    /** If true, the expiration time of the resource is measured from receipt,
	rather than from manufacturing */
    boolean resetExpiration=false;
    
    /** Performs certain auxiliary operation piggy-backed on acceptance
     */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	//	if (whose.getName().equals("eeCmoProd")) {
//	    System.out.println("DEBUG: " + getName() + ".accept(" + amount +
//			       ") from " + provider);
//	}
	double now = state.schedule.getTime();
	if (resetExpiration) ((Batch)amount).resetExpiration(now);
	
	return doAccept(provider,  amount, atLeast, atMost, false);
    }

    /** @param isInit If true, just puts stuff in, without any additional
	operations. This is a call during the initialization
    */
    public boolean doAccept(Provider provider, Resource amount, double atLeast, double atMost, boolean isInit) {
	//	    String given = (amount instanceof CountableResource)? ""+  amount.getAmount()+" units":		(amount instanceof Batch)? "a batch of " + ((Batch)amount).getContentAmount() +" units":		"an entity";

	double a = (amount instanceof Batch)? ((Batch)amount).getContentAmount() : amount.getAmount();

	if (!prototype.isSameType(amount)) throw new AssertionError(getName() + " receiving " + amount+ ", from " + provider);


	boolean z = super.accept(provider,  amount, atLeast,  atMost);
	if (!z) throw new AssertionError();
	currentStock += a;

	if (isInit) return z; // it's no time to do anything else as the system is not ready yet
	
	everReceived  += a;

	boolean fromMagic = (safety!=null) && safety.comesFromMagicSource(provider);
	if (fromMagic) everReceivedFromMagic += a;
	
	if (fromMagic) receivedTodayFromMagic += a;
	else receivedTodayFromNormal += a;

	
	
	// See if the production system is empty, and needs to be "primed"
	// to start.
	if (whose.needsPriming()) {
	    double t = state.schedule.getTime();
	    //System.out.println("At " + t + ", the "+getName()+" tries to prime " + whose.getName());
	    
	    // This will "prime the system" by starting the first
	    // mkBatch(), if needed and possible. After that, the
	    // production cycle will repeat via the slackProvider
	    // mechanism
	    whose.mkBatch();
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
	//if (safety!=null) s += "; S=" + safety.getAvailable();
	s += ")";
	return s;
    }

    String report(boolean showBatchSize)  {
	Vector<String> v= new Vector<>();
	v.add(  getTypicalProvided().getName() +":" +
		(long)getContentAmount());
		//	    (getTypicalProvided() instanceof Batch? 
		//	     (long)getAvailable() + " ba" :
		//	     getAvailable() + " u" ));

	if (expiredProductSink!=null) v.add( expiredProductSink.reportShort());
	if (stolen>0) v.add(" (Stolen=" + stolen +  ")");
	String s = Util.joinNonBlank(". ", v);

	if (safety==null || everReceived!=safety.everReceived) {
	    s += ". Received " + everReceived;
	    if (everReceivedFromMagic!=0) s += " (incl. "+everReceivedFromMagic+" from SS)";
	} else {
	    s += ". " + safety.report();
	}
	
	return s;
    }


    /** The time when the current anomaly was first detected, or null
	if there is no anomaly now. This var is set daily by  detectAnomaly.
     */
    //private Double anomalySince=null;
    

    /** Have we had a resource inflow anomaly during the precedingh 24 hrs period? */
    /* boolean detectAnomaly() {
	double t = state.schedule.getTime();
	boolean anomalyToday=detector.test(t, everReceived, false);
	if (anomalyToday) {
	    if (anomalySince==null) anomalySince=t;
	} else {
	    anomalySince = null;
	}
	return  anomalyToday;
	}*/
   

    /*
    void linkUp(HashMap<String,Steppable> knownPools) throws IllegalInputException {

	if (para!=null) {
	    Vector<String> mtos = para.get("mto");	    
	    if (mtos!=null) {
		if (mtos.size()!=2) throw new IllegalInputException("mtos=" + String.join(",",mtos));
		String name2 = mtos.get(0);
		Steppable _from =  knownPools.get(name2);
		if (_from==null || !(_from instanceof BatchProvider2)) throw new  IllegalInputException("There is no provider unit named '" + name2 +"', in " + getName() + ",mto");
		mtoSource = (BatchProvider2)_from;
		mtoChannel = new Channel(mtoSource, this, getName());
		
		mto = Double.parseDouble(mtos.get(1));
		if (mto!=null && mto<1.0) throw new IllegalInputException("mto=" + mto);
	    }
	}  

	if (safety!=null) {
	    safety.linkUp(knownPools);
	}
    }
    */


}

