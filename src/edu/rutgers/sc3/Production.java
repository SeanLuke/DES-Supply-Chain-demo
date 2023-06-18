package  edu.rutgers.sc3;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;


/** Storing a raw material (fungible CountableResource) for a production unit.
    The buffer is self-replenishable, sending orders to the suppliers when needed.
 */
class Production extends AbstractProduction
    implements Reporting, Named 
{

    double now() {
	return  state.schedule.getTime();
    }

    /** Is this a manually controlled node? */
    private final boolean manual;
    
    /** The buffers for inputs (raw materials) that go into production */
    private InputStore[] inputStore;
    public sim.des.Queue[] getInputStore() { return inputStore;}

    private Charter charter;

    /** This could be a ProdDelay, a ThrottledStage, or a Pipeline, as needed */
    private Middleman _prodStage;
    private <T extends Middleman & NeedsPriming & Reporting.HasBatches> T  prodStage() {
	return (T)_prodStage;
    }
    //    private ProdDelay prodDelay;
    //    private final ThrottleQueue needProd;

    /** Transportation from the production to the QA stage, as may
	exist in some tracks. It is null if transportation takes no time
	in the model.
    */
    private SimpleDelay transDelay = null;
    
    /** These only exist if the respective stages are throttled (FIFO,
	capacity-1), rather than "parallel" (infinite capacity) */
    private final ThrottleQueue needTrans, needQa;

    interface NeedsPriming {	
	/** This is the property of certain network elements (throttled stages),
	    who need to be given a batch first to get them started, after which
	    they keep grabbing batches */
	default boolean needsPriming() { return false; }
	/** Passes the request to the ProdDelay */
	void setFaultRateIncrease(double x, Double _untilWhen);
	/** Is this production stage (or its first substage) unconstrained
	    by capacity? It is true for a lone (unthrottled) ProdDelay;
	    false for throttled system. */
	default boolean unconstrained() { return (this instanceof ProdDelay); }
	//double getEverReleased();
    }
    
    /** Returns true if the production step is empty, and one
	should see if it needs to be reloaded */
    boolean needsPriming() {
	return prodStage().needsPriming();
    }
    
    /** If an external producer sends it product for us to do QA, this
	is where it should be sent */
    //    ThrottleQueue getNeedQa() { return needQa;}
    /** If an external producer sends it product for us to do QA, this
	is where it should be sent. This is either the QA delay itself (if
	parallel processing is allowed), or the waiting buffer (if throttled FIFO processing).
    */
    Receiver getQaEntrance() { return needQa!=null? needQa: qaDelay;}

    /** Where batches go for transportation. Either the transportation delay
	(if unlimited capacity) or the pre-transportation queue (if throttled)
     */
    Receiver getTransEntrance() { return needTrans!=null? needTrans: transDelay; }
	


    //public ProdDelay getProdDelay() { return prodDelay; }

    /** Returns the last existing stage of this production unit. Typically
	this is the qaDelay, but some units (CMO Track A) don't have QA,
	so this will be the transportation delay, or even the production
	delay.  The main use of this method is so that we can add a Receiver
	to the Provider returned by this call, thus enabling this Production
	to send its product to the next element of the supply chain.
    */
    public Provider getTheLastStage() {
	return qaDelay!=null? qaDelay:
	    transDelay!=null? transDelay: prodStage();
    }

    /** A Recipe contains information about the inputs needed to
	produce a batch of an output product. 
     */
    private class Recipe {
	/** How many units of each input need to be taken to start cooking a batch? */
	final double[] inBatchSizes;
	/** How big is the output batch? */
	final double outBatchSize;

	Recipe(ParaSet para) throws  IllegalInputException { //, CountableResource underlying, boolean useDefault) {

	    final String suff = ""; //useDefault ? "" : "." + underlying.getName();

	    outBatchSize = para.getDouble("batch" + suff);
	    
	    inBatchSizes = (inResources.length==0)? new double[0]: para.getDoubles("inBatch" + suff);
	    if (inBatchSizes.length!=nin) throw new  IllegalInputException("Mismatch of the number of inputs for "+getName()+ suff + ": given " + nin + " resources ("+Util.joinNonBlank(";",inResources)+"), but " + inBatchSizes.length + " input batch sizes");
	}	
    }

    /** The number of input commodities */
    final int nin; 
    private final Recipe recipe; // [];
	
   /** What is the "entry point" for input No. j? */
    Receiver getEntrance(int j) {
	return inputStore[j];
    }

    /** Creates a transportation delay in front of input buffer No. j,
	and returns it so that it can be used as an entry point (
	instead of the usual getEntrance()). The delay parameters
	must be present in the config time.
     */
    Receiver mkInputDelay(int j) throws IllegalInputException {
	String key="inputDelay."+j;
	AbstractDistribution d =  para.getDistribution(key ,state.random);
	if (d==null) throw new  IllegalInputException("Production unit " + getName() + " does not have an " + key + " in its parameter set");
	Delay delay  = new Delay(state, outResource);
	//if (getEntrance(j)==null) throw new AssertionError();
	delay.addReceiver(getEntrance(j));
	delay.setDelayDistribution( d);
	return delay;
    }
	

    
    final Resource[] inResources;
    final Batch outResource; 


    /** Simulates stolen shipments */
    final MSink stolenShipmentSink;



    final ParaSet para;
    ParaSet getPara() { return para; }
    
    private SimState state;

    /** How many different output products can this Production node produce?
	(Historically, it was 1, but in SC-3 multiple products can be produced)
    */
    //final int nout;
    
    /** @param inResource Inputs (e.g. API and excipient). Each of them is either a (prototype) Batch or a CountableResource
	@param outResource batches of output (e.g. bulk drug)
     */    
    Production(SimState _state, String name, Config config,
	       Resource[] _inResources,
	       Batch _outResource ) throws IllegalInputException, IOException
    {
	state = _state;
	inResources =  _inResources;
	nin = 	inResources.length;
	outResource = _outResource;
	setName(name);
	para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	manual = para.getBoolean("manual", false);

	//-- what products we can produce
	//CountableResource [] oru = outResource.listUnderlying();
	//nout = oru.length;

	recipe = new Recipe(para);
	
	//for(int j=0; j<nout; j++) {
	//    recipe[j] = new Recipe(para, oru[j], false);
	//}

	
	// Storage for input ingredients
	inputStore = new InputStore[nin];
	for(int j=0; j<nin; j++) {
	    inputStore[j] =
		new InputStore(this, state, config, inResources[j]);

	    if (this instanceof Macro) addReceiver(inputStore[j], false); 
	}
	

	//-- Are trans and QA stages throttled (FIFO) or parallel?
	final boolean qaIsThrottled= para.getBoolean("qaThrottled", false),
	    transIsThrottled=para.getBoolean("transThrottled", false);
	
	//batchesPerDay = para.getLong("batchesPerDay", null);

	double cap = (outResource instanceof Batch) ? 1:  recipe.outBatchSize;	

	qaDelay = QaDelay.mkQaDelay( para, state, outResource);
	if (qaDelay != null) {
	    if (this instanceof Macro)  addProvider(qaDelay, false);
	    if ( qaIsThrottled) {
		AbstractDistribution d = para.getDistribution("qaDelay",state.random);
		boolean unit = (d==null);
		if (unit)  d = para.getDistribution("qaDelayUnit",state.random);
		if (d==null) throw new IllegalInputException("No qaDelay or qaDelayUnit in param set for " + getName()); 
		needQa =new ThrottleQueue(qaDelay, cap, d, unit);
		needQa.setWhose(this);
	    } else {
		needQa = null;
	    }
	} else {
	    needQa = null;
	}

	if (para.get("transDelay")!=null) {
	    AbstractDistribution d =  para.getDistribution("transDelay",state.random);
	    
	    if (transIsThrottled) {
		transDelay = new SimpleDelay(state, outResource);
		// if there is a trans delay, its cost is  always batch-based (not unit-based) cost
		needTrans = new ThrottleQueue(transDelay, cap, para.getDistribution("transDelay",state.random), false);
	    } else {
		transDelay = new Delay(state, outResource);
		((Delay)transDelay).setDelayDistribution(  d);		
		needTrans = null;
	    }
	    transDelay.setName(getName() + ".TransDelay");// + outResource.getName());
	    
	    if (getQaEntrance()!=null) {
		transDelay.addReceiver(getQaEntrance());
		//System.out.println("DEBUG: " + transDelay.getName() +" feeds to " + getQaEntrance().getName());
	    }  else {		
		// The receiver will be added later, in the SplitManager() call

		//System.out.println("DEBUG: " + transDelay.getName() +
		//			   " feeds to nowhere yet; should be connected to SM later");
		
		//throw new IllegalInputException("In " + getName() +", there is " + transDelay.getName() +", but we don't know where it feeds to");
	    }
		
	} else {
	    transDelay = null;
	    needTrans = null;
	}
	
	ProdDelay prodDelay = new ProdDelay(state,this,outResource);

	Receiver w = (getTransEntrance()!=null) ? getTransEntrance(): getQaEntrance();
	if (w!=null) prodDelay.addReceiver(w);

	AbstractDistribution d0 = para.getDistribution("prodDelay",state.random);
	AbstractDistribution dn = (d0==null)? null: new CombinationDistribution(d0, (int)recipe.outBatchSize);

	AbstractDistribution d = para.getDistribution("prodDelay",state.random);
	boolean unit = (d==null);
	if (unit)  d = para.getDistribution("prodDelayUnit",state.random);

	if (d!=null) {
	    ThrottleQueue needProd = new ThrottleQueue(prodDelay, cap, d, unit);

	    needProd.setWhose(this);
	    needProd.setAutoReloading(true);
	    _prodStage = new ThrottledStage(state, needProd, prodDelay);
	} else {
	    // Production delay is not specified; thus we assumed that
	    // production is (nearly) instant, as it's the case for
	    // RM EE supplier in SC-2. Therefore it's not throttled...
	    // A kludge for nearly-instant production
	    prodDelay.setDelayTime(0.0001);
	    _prodStage = prodDelay;
	}
	
	if (qaDelay !=null && qaDelay.reworkProb >0) {
	    qaDelay.setRework( prodStage());
	}

	stolenShipmentSink = new MSink(state, outResource);
	stolenShipmentSink.setName("StolenShipmentsFrom." + getName());

    //System.out.println("DEBUG: in " + getName() +", " + getTheLastStage().getName() +			   " feeds to SM");

	
	sm = new SplitManager(this, outResource, getTheLastStage());

	charter=new Charter(state.schedule, this);
	//	String moreHeaders[] = new String[2 + 2*inResources.length];
	String moreHeaders[] = new String[2 + 3*inResources.length];
	int k = 0;
	moreHeaders[k++] = "releasedToday";
	moreHeaders[k++] = "outstandingPlan";
	for(int j=0; j<inputStore.length; j++) {
	    String rn = inputStore[j].getUnderlyingName();
	    moreHeaders[k++] = "Stock." + rn;
	    moreHeaders[k++] = "ReceivedTodayFromNormal." + rn;
	    moreHeaders[k++] = "ReceivedTodayFromMagic." + rn;
	}
	//for(int j=0; j<inputStore.length; j++) {
	//    moreHeaders[k++] = "Anomaly." + inputStore[j].getUnderlyingName();
	//}
	charter.printHeader(moreHeaders);

	Vector<String> outputName = para.get("output");
	if (outputName!=null) {
	    throw new AssertionError("Using outputName (" +outputName+") is not supported yet");
	}


	
    }

    /** Have this Production unit make use of another unit's
	input buffers, instead of its own. This is used in SC2 2.000
	for backup CMO prods.
    */
    void shareInputStore(Production other) {
	inputStore = other.inputStore;
	if (inputStore.length != inResources.length) throw new IllegalArgumentException();
	// FIXME...
	//for(int j=0; j<inputStore.length; j++) {
	//    if (this instanceof Macro) addReceiver(inputStore[j], false); 
	//}
    }
	
    
  /** Do we have enough input materials of each kind to make a batch? 
	FIXME: Here we have a simplifying assumption that all batches are same size. This will be wrong if the odd lots are allowed.
     */
    private boolean hasEnoughInputs() {
	for(int j=0; j<nin; j++) {
	    if (!inputStore[j].hasEnough(recipe.inBatchSizes[j])) return false;
	}
	return true;
    }


    int batchesStarted=0;
    double everStarted = 0;

    public double getEverStarted() { return everStarted; }

    /** Good resource released by QA as of yesterday. Used to compute
	the size of today's output, for use in charting */
    private double releasedAsOfYesterday=0;



    /** Checks if there are any "depletion" disruptions on any of our
	input resources. The "afected unit" name in the disruption
	entry should be of the form
	productionUnitName.resourceName. For example, "eeCmoProd.RMEE"
	will deplete the input store of RMEE (raw material for EE) at
	the eeCmoProd unit.

       The 
    */
    private void disruptInputs(SimState state) {
	//if (getName().startsWith("Cmo")) return;

	double now = state.schedule.getTime();
	
	for(int j=0; j<nin; j++) {

	    InputStore p = inputStore[j];
	    String rname = p.getUnderlyingName();
	    String dname = getName() +  "." + rname;

	    Disruptions.Type type = Disruptions.Type.Depletion;
	    
	    Vector<Disruption> vd = ((Demo)state).hasDisruptionToday(type, dname);
	    if (vd.size()==1) {
		// deplete inventory
		//double amt = Math.round(vd.get(0).magnitude * 1e7);
		double amt = Math.round(vd.get(0).magnitude);
		double x = p.deplete(amt);
		if (!Demo.quiet) System.out.println("Input buffer " + dname + ": disruption could destroy up to " + amt + " units, actually destroys " + x);

	    } else if (vd.size()>1) {
		throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	    }

	    type = Disruptions.Type.DisableTrackingSafetyStock;
	    
	    vd = ((Demo)state).hasDisruptionToday(type, dname);
	    if (vd.size()==1) {
		// stop SS level tracking for a while
		double days = vd.get(0).magnitude;

		if (p.safety==null) {
		    throw new IllegalArgumentException("It is impossible to disable the safety stock (disruption " + vd.get(0) +" on input buffer " + p + "), because that input buffer has no safety stock to begin with");
		}
		if (!Demo.quiet) System.out.println("Input buffer " + dname + ": at "+now+", disruption stops SS level tracking for " + days + " days");
		p.safety.haltUntil( now+days );
		
	    } else if (vd.size()>1) {
		throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	    }

    
	}
	
    }

    /** Indicates whether the node has been halted by a disruption */
    private Timed haltedUntil = new Timed();
    /** For manually controlled nodes, this timer is used to
	indicate that the node has been turned on */
    private Timed manualOnUntil = new Timed();

    /** Checks if this production unit is operational. For manually
	controlled units, we check if it has been turned on; for all
	units, we check that there isn't a "Halt" disruption in effect.
    */
    boolean isOpenForBusiness(double now) {
	return (!manual || manualOnUntil.isOn(now)) &&
	    !haltedUntil.isOn( now );
    }


    /** Orders that this Production unit is yet to fill. 
     */
    private Vector<Order> needToSend = new Vector<>();

    /** Tell this production unit to fill orders that some other
	unit has on file. This is used in SC2 ver. 2.* when this
	is a backup unit meant to substitute for another unit.
    */       
    void sharePlan(Production other) {
	needToSend = other.needToSend;
    }
	
    
    double sumNeedToSend() {
	double sum = 0;
	for(Order e: needToSend) sum += e.amount;
	return sum;
    }
 
    private boolean noPlan = false;
    
    /** If this is not null, it indicates how many units of the product we
	are still to produce (or, more precisely, to start). If null,
	then the control is entirely by the supply side. */
    //Double startPlan = 0.0;

    /** Configure this unit to be controlled by the rationing of inputs */

    private double everPlanned = 0;
    
    public void setNoPlan() {
	noPlan = true;
	//startPlan = null;
    }
    //void setPlan(double x) { startPlan = x; }

    //void addToPlan(double x) {
    //	if (x<0) throw new IllegalArgumentException(getName() +".addToPlan(" + x+")");
    //	everPlanned += x;
    //	if (startPlan != null) x += startPlan;
    //	startPlan = x;
    //}
    public void addToPlan(Order order) {
	if (order==null || order.amount<0) throw new IllegalArgumentException(getName() +".addToPlan(" + order+")");
	if (order.amount==0) return;
	everPlanned += order.amount;
	//	if (startPlan != null) x += startPlan;
	//startPlan = x;
	needToSend.add(order.copy()); // use a copy, to enable later subtractions
    }


    /** Removes the specified order (or what's left of it) if it's still sitting in the back-order queue */
    public void cancel(Order order) {


	//if (order.channel.isInfoHalted(now())) {
	//    if (!Demo.quiet) System.out.println("At " + now()+", " + getName() + " ignored cancelation(" + order
	//						+") because of info disruption");
    //    return;
    //	}

	for(int j=0; j<needToSend.size(); j++) {
	    if (needToSend.get(j).id == order.id) {
		if (!Demo.quiet) System.out.println("At " + now()+", " + getName() + " canceling order: " + needToSend.get(j));
		needToSend.remove(j);
		return;
	    }
	}
	if (!Demo.quiet) System.out.println("At " + now()+", " + getName() + " cannot cancel already executed order: " + order);
    }
    
    /** After a batch has been made, subtracts it from the plan */
    private void recordProduction(double amt) {
	if (noPlan) return;
       	while(!needToSend.isEmpty() && amt>0) {
	    Order order = needToSend.get(0);
	    double r = Math.min(order.amount, amt);
	    amt -= r;
	    order.amount -= r;
	    if (order.amount==0) needToSend.remove(0);
	}
    }

    private double everStolen=0;

    /** Destroys some shipments in the transportation delay (between production and QA) */
    private void disruptShipments(SimState state) {
	everStolen += 	ShipmentLoss.disruptShipments( state,  getName(), transDelay);
    }
    
      /** See if the production plan and the available inputs mandate the
	production of more batches, and if so, ensure that the prodDelay
	is 'primed' by needProd.

	This method also checks for disruptions, and (for manually
	controlled units) for the "On" command. A disruption may halt
	the unit, or reduce the production capacity temporarily.
    */
    public void step(SimState state) {

	try {
	    disruptInputs( state);
	    disruptShipments( state);

	    double now = state.schedule.getTime();
	    
	    for(Disruption d:  ((Demo)state).hasDisruptionToday(Disruptions.Type.Adulteration, getName())) {
		
		// reduce quality of newly produced lots, in effect for 1 day
		//double r = 0.1 * d.magnitude;
		double r = d.magnitude;
		if (!Demo.quiet)  System.out.println("At t=" + now + ", Production unit "+ getName() +" increasing failure rate by " + r +", until " + (now+1));
		prodStage().setFaultRateIncrease(r, now+1);
	    }


	    Disruptions.Type type = Disruptions.Type.Halt;
	    for(Disruption d: ((Demo)state).hasDisruptionToday(type, getName())) { 
		haltedUntil.enableUntil( now+d.magnitude );
		if (!Demo.quiet)  System.out.println("At t=" + now + ", Production unit "+ getName() +" started disruption '"+type+"' until " + (now+d.magnitude));
	    }

	    type = Disruptions.Type.On;
	    for(Disruption d: ((Demo)state).hasDisruptionToday(type, getName())) { 
		manualOnUntil.enableUntil( now+d.magnitude );
		if (!Demo.quiet)  System.out.println("At t=" + now + ", Production unit "+ getName() +" received command '"+type+"' until " + (now+d.magnitude));
	    }
   
	    
	    if (!hasEnoughInputs()) {
		if (Demo.verbose)
		    System.out.println("At t=" + now + ", "+ getName() + " production is starved. Input stores: " + reportInputs(true));
		return;
	    }


	    if (prodStage().unconstrained()) {
		// If the production stage apparently allows
		// uncosntrained parallel processing, put everything
		// into it		
		int n = 0;
		while( mkBatch()) {
		    n++;
		}		
	    } else if (prodStage().needsPriming()) {
		// This will "prime the system" by starting the first
		// mkBatch(), if needed and possible. After that, the
		// production cycle will repeat via the slackProvider
		// mechanism
		mkBatch();
	    }		       

	} finally {
	    dailyChart();
	}
	
    }

    
    /** Writes this days' time series values to the CSV file. 
	Does that for the safety stocks too, if they exist.
	Here we also check for the inflow anomalies in all
	buffers.  This method needs to be called from Production.step(),
	to ensure daily execution.
    */
    private void dailyChart() {

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


 
    }
    

    /** Can we produce odd lots? Normally we can; but, for arithmetic
	simplicity, proration is not allowed if some of the inputs
	have a different input:output ratio than 1:1.
     */
    private boolean canProrateLots() {
	for(double x: recipe.inBatchSizes) {
	    if (x!=recipe.outBatchSize) return false;
	}
	return true;
    }

    
    /** Tries to make a batch and put it into prodDelay (or its
	waiting queue), if resources are available
	@return true if a batch was made; false if not enough input resources
	was there to make one, or the current plan does not call for one

    */
    public boolean mkBatch() {

	double need=sumNeedToSend();
	// (startPlan == null)? 0: startPlan;
	if (!noPlan && need <= 0) return false;

	double now = state.schedule.getTime();

	
	if (!isOpenForBusiness(now)) {
	    //System.out.println(getName()+ " H");
	    return false;
	}
	//boolean debug = getName().equals("dsCmoBackupProd");
	//if (debug) System.out.println(getName()+ ", t="+t+", has inputs=" + hasEnoughInputs());
	if (!hasEnoughInputs()) return false;
		
	Vector<Batch> usedBatches = new Vector<>();

	boolean prorate = !noPlan && (need < recipe.outBatchSize) &&   canProrateLots();

	for(int j=0; j<nin; j++) {
	    
	    InputStore p = inputStore[j];
	    //System.out.println("mkBatch: Available ("+p.getTypicalProvided()+")=" + p.reportAvailable());

	    double ne = recipe.inBatchSizes[j];
	    if (prorate) ne = (ne * need) / recipe.outBatchSize;
	    
	    Batch b = p.consumeOneBatch(ne);
	    if (b!=null) usedBatches.add(b);    
	}

	//	if (Demo.verbose) System.out.println("At t=" + now + ", " + getName() + " starts a batch; still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());


	double outAmt = (prorate)? need: recipe.outBatchSize;
	Batch onTheTruck = outResource.mkNewLot(outAmt, now, usedBatches);
	Provider provider = null;  // why do we need it?		
	prodStage().accept(provider, onTheTruck, 1, 1);

	batchesStarted++;
	everStarted += outAmt;
	//	if (startPlan != null) startPlan -= outAmt;
	recordProduction(outAmt);
	
	return true;

    }
    
    private String reportInputs(boolean showBatchSize) {
	Vector<String> v= new Vector<>();
	int j=0;
	for(InputStore input: inputStore) {	    
	    v.add( input.report(showBatchSize));
	    j++;
	}
	return "[" + String.join(", ",v) + "]";
    }

    private String reportInputs() {
	return  reportInputs(false);
    }


    public double getDiscarded() {
	return (qaDelay!=null) ? qaDelay.badResource : 0;
    }


    /** FIXME: this is not entirely correct if qaDelay is absent. It would be
	better to report how many units have come out of transDelay, or
	if absent, from prodDelay. But typically we are just off by 1 batch.
     */
    public double getReleased() {
	return  (qaDelay!=null) ? qaDelay.getReleasedGoodResource():
	    //prodDelay.getTotalStarted(); // ZZZZ
	    prodStage().getEverReleased();
    }

    public String report() {
	String ba = outResource instanceof Entity? " ba": " u";

	String s = "[" + cname()+"."+getName();
	if (inputStore.length>0) {
	    s += "; stored inputs=("+ reportInputs() +")";
	}
	s += noPlan?
	    ". No planning (driven by input)" :
	    ". Ever planned: "+(long)everPlanned + "; still to do "+sumNeedToSend();
	s +=
	    ". Ever started: "+(long)everStarted + " ("+batchesStarted+" ba)";

	s += " = (in prod=" + prodStage().hasBatches() + ba +")";
	if (qaDelay!=null) {
	    if (needQa!=null) s += " (Waiting for QA=" + (long)needQa.getAvailable() +")";
	    s += " " + qaDelay.report();	    
	//s +="  in QA=" +   needQa.hasBatches() +")";
	} else {
	}
	    
	//	s += "\n" + prodStage().report();

	//if (stolenShipmentSink.getEverConsumed()>0) s+="\n" + stolenShipmentSink.report();
	if (everStolen>0) s+="\nLost in shipment " + everStolen + " u";
	
	if (sm.outputSplitter !=null) 	s += "\n" + sm.outputSplitter.report();

	s+="]";
	return s;

    }


    /** Produces an optional Delay element that can be stuck at the output
	end of this Production unit. Configured based on the "outputDelay"
	field in the ParaSet.
    */
    Delay mkOutputDelay(Receiver rcv)  throws IllegalInputException {

	AbstractDistribution distr = para.getDistribution("outputDelay", state.random); 
	Delay delay = new Delay(state, outResource);
	delay.setDelayDistribution(distr);
	delay.addReceiver(rcv);
	return delay;
    }
       
}
