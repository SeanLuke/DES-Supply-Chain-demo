package edu.rutgers.sc3;

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
    implements Reporting, BatchProvider2
{

    double now() {
	return  state.schedule.getTime();
    }

    /** Is this a manually controlled node? */
    private final boolean manual;
    
    /** The buffers for inputs (raw materials) that go into production */
    private InputStore[] inputStore;
    public sim.des.Queue[] getInputStore() { return inputStore;}

    /** The tool for saving time series files. It is initialized before
	first use, rather than in the Production constructor, because
	their formatting depends on whether this Production node has
	its own input buffers or shares someone else's.
    */
    private Charter charter=null;

    /** This could be a ProdDelay, a ThrottledStage, or a Pipeline, as needed */
    private Middleman _prodStage;
    <T extends Middleman & NeedsPriming & Reporting.HasBatches & Reporting> T  prodStage() {
	return (T)_prodStage;
    }
    //    private ProdDelay prodDelay;
    //    private final ThrottleQueue needProd;

    /** Transportation from the production to the QA stage, as may
	exist in some tracks. It is null if transportation takes no time
	in the model.
    */
    private CustomDelay transDelay = null;
    
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
	return
	    qaDelay!=null? (qaDelay.postQaJoin != null?  qaDelay.postQaJoin:

			    qaDelay):
	    transDelay!=null? transDelay: prodStage();
    }

    /** A Recipe contains information about the inputs needed to
	produce a batch of an output product. 
     */
    class Recipe {
	/** How many units of each input need to be taken to start cooking a batch? */
	final double[] inBatchSizes;
	/** How big is the output batch? */
	final double outBatchSize;

	Recipe(ParaSet para, String suff) throws  IllegalInputException { //, CountableResource underlying, boolean useDefault) {

	    //final String suff = ""; //useDefault ? "" : "." + underlying.getName();

	    outBatchSize = para.getDouble("batch" + suff);
	    
	    inBatchSizes = (inResources.length==0)? new double[0]: para.getDoubles("inBatch" + suff);
	    if (inBatchSizes.length!=nin) throw new  IllegalInputException("Mismatch of the number of inputs for "+getName()+ suff + ": given " + nin + " resources ("+Util.joinNonBlank(";",inResources)+"), but " + inBatchSizes.length + " input batch sizes");
	}	
    }

    private Recipe readRecipe(ParaSet para, String suff) throws  IllegalInputException {
	if (para.getDouble("batch" + suff, null)==null) return null;
	return new Recipe(para, suff);
    }
	



    /** The number of input commodities */
    final int nin;
    /** The recipe for making output from inputs */
    private final Recipe recipe; // [];
    /** The recipe for MTO orders  */
    private Recipe recipeMto; 
	
   /** What is the "entry point" for input No. j? */
    InputStore  getEntrance(int j) {
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

    /** I added this because the Filter class is, strangely, abstract! */
    /*
    static class SimpleFilter extends Filter {
	public SimpleFilter(sim.engine.SimState state, Resource typical) {
	    super(state, typical);
	}
    }
    */
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

	if (para.getBoolean("noPlan", false)) setNoPlan();
	
	manual = para.getBoolean("manual", false);

	AbstractDistribution od = para.getDistribution("orderDelay" ,state.random);
	if (od!=null) {
	    orderDelay = new OrderDelay(state, this);
	    orderDelay.setDelayDistribution( od);
	}
	
	//-- what products we can produce
	//CountableResource [] oru = outResource.listUnderlying();
	//nout = oru.length;

	recipe = new Recipe(para, "");

	recipeMto = readRecipe(para, ".mto");
	if (recipeMto==null) recipeMto=recipe;
	
	//for(int j=0; j<nout; j++) {
	//    recipe[j] = new Recipe(para, oru[j], false);
	//}

	
	// Storage for input ingredients
	inputStore = new InputStore[nin];
	for(int j=0; j<nin; j++) {
	    //System.out.println("DEBUG: InputStore(r="+inResources[j]+")");
	    inputStore[j] =
		new InputStore(this, state, config, inResources[j]);

	    if (this instanceof Macro) addReceiver(inputStore[j], false); 
	}
	

	//-- Are trans and QA stages throttled (FIFO) or parallel?
	final boolean qaIsThrottled= para.getBoolean("qaThrottled", false),
	    transIsThrottled=para.getBoolean("transThrottled", false);
	
	//batchesPerDay = para.getLong("batchesPerDay", null);

	double cap = (outResource instanceof Batch) ? 1:  recipe.outBatchSize;	

	qaDelay = QaDelay.mkQaDelay( config, para, state, this,  outResource);
	
	if (qaDelay != null) {

	    AbstractDistribution d=para.getDistribution("qaDelay",state.random);
	    boolean unit = (d==null);
	    if (unit)  d = para.getDistribution("qaDelayUnit",state.random);
	    if (d==null) throw new IllegalInputException("No qaDelay or qaDelayUnit in param set for " + getName());
	    DelayRules dr = new DelayRules(d, unit, qaDelayFactorUntil);
	    qaDelay.setDelayRules(dr);

	    if (this instanceof Macro)  addProvider(qaDelay, false);
	    if ( qaIsThrottled) {		
		needQa =new ThrottleQueue(qaDelay, cap);
		needQa.setWhose(this);
	    } else {
		needQa = null;
	    }
	} else { //-- no QA step at all
	    needQa = null;
	}

	if (para.get("transDelay")!=null) {
	    
	    AbstractDistribution d =  para.getDistribution("transDelay",state.random);
	    // if there is a trans delay, its cost is  always batch-based (not unit-based) cost
	    DelayRules dr = new DelayRules(d, false, transDelayFactorUntil);
	    transDelay = new CustomDelay(state, outResource);
	    transDelay.setDelayRules(dr);

	    if (transIsThrottled) {
		needTrans = new ThrottleQueue(transDelay, cap);
	    } else {
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


	Integer nProdStages = para.getInt("prodStages", null);
	if ( nProdStages == null) {
	    _prodStage = mkProdDelay("");
	} else if ( nProdStages == 1) {
	    _prodStage = mkProdDelay(".1");
	} else if ( nProdStages > 1) {
	    Pipeline p = new Pipeline(state, outResource);
	    p.setName( getName() + ".pipeline");
	    for(int j=0; j<nProdStages; j++) {
		p.addStage( mkProdDelay("."+(j+1)));
	    }
	    _prodStage = p;
	} else {
	    throw new IllegalInputException("Unexpected number of stages: " + nProdStages);
	}

	Receiver w = (getTransEntrance()!=null) ? getTransEntrance(): getQaEntrance();
	if (w!=null) {
	    prodStage().addReceiver(w);
	    if (Demo.verbose) System.out.println( "INNER_LINK: " + prodStage().getName() + " sends to "+ w.getName());
	}



	    
	stolenShipmentSink = new MSink(state, outResource);
	stolenShipmentSink.setName("StolenShipmentsFrom." + getName());

    //System.out.println("DEBUG: in " + getName() +", " + getTheLastStage().getName() +			   " feeds to SM");

	
	sm = new SplitManager(this, outResource, getTheLastStage());

	//	Vector<String> outputName = para.get("output");
	//if (outputName!=null) {
	//    throw new AssertionError("Using outputName (" +outputName+") is not supported yet");
	//}
	
    }

    /** Creates the prod delay (throttled, if needed).

	<p>If the production delay time is not specified in the config
	file, we assumed that // production is (nearly) instant, as
	it's the case for // RM EE supplier in SC-2. Therefore it's
	not throttled...  // A kludge for nearly-instant production

	
	@param suff Either "", or ".1" etc, to be used in names. This is also controls who will get the auto-reloading feature: only the first stage needs it, because only the first stage needs to trigger mkBatch()
    */
    Middleman mkProdDelay(String suff) throws IllegalInputException {

	ProdDelay prodDelay = new ProdDelay(state, this, suff);


	AbstractDistribution d = para.getDistribution("prodDelay"+suff,state.random);
	boolean unit = (d==null);
	if (unit)  d = para.getDistribution("prodDelayUnit"+suff,state.random);
	DelayRules dr = (d!=null) ?
	    new DelayRules(d, unit, prodDelayFactorUntil):
	    new DelayRules(0.0001);
	
	prodDelay.setDelayRules(dr);
	
	boolean throttled = para.getBoolean("prodThrottled"+suff, d!=null);
	
	if (throttled) { // throttled, i.e. 1 batch at a time
	    final double cap = 1;
	    ThrottleQueue needProd = new ThrottleQueue(prodDelay, cap);
	    
	    needProd.setWhose(this);
	    if (suff.equals("") || suff.equals(".1")) needProd.setAutoReloading(true);
	    ThrottledStage q = new ThrottledStage(state, needProd, prodDelay);
	    q.setName( getName() + ".prodThrottledStage" + suff);
	    return q;
	} else { //concurrent (with no cap), i.e. multiple batches at a time
	    return prodDelay;
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
	@param ratio Usually {1,1}, but can represent some ratio (say {1,3} for 1/3) for prorating lots
     */
    private boolean hasEnoughInputs(double[] ratio) {

	boolean debug = false; //getName().equals("cellProd");	
	for(int j=0; j<nin; j++) {
	    double ne = (ratio[0]*recipe.inBatchSizes[j])/ratio[1];
	    if (!inputStore[j].hasEnough(ne)) {
		if (debug) {
		    System.out.println(inputStore[j].getName() +
				       ", ratio="+Util.joinNonBlank("/",ratio)+", not enough " + 
				       "; need " + ne);
		}
		return false;
	    }
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
	    // The name of an input buffer, e.g. LargeArrayAssembly.diode
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
		double end = vd.get(0).end();

		if (p.safety==null) {
		    throw new IllegalArgumentException("It is impossible to disable the safety stock (disruption " + vd.get(0) +" on input buffer " + p + "), because that input buffer has no safety stock to begin with");
		}
		if (!Demo.quiet) System.out.println("Input buffer " + dname + ": at "+now+", disruption stops SS level tracking until t=" + end);
		p.safety.haltUntil( end );
		
	    } else if (vd.size()>1) {
		throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	    }


	    type = Disruptions.Type.TransDelayFactor;	    
	    vd = ((Demo)state).hasDisruptionToday(type, dname);
	    if (vd.size()==1) {
		// Slow down the stream of supplies
		Disruption d = vd.get(0);
		double end = d.end();

		if (p.safety==null) {
		    throw new IllegalArgumentException("It is impossible to slow down supplies of stock (disruption " + d +" on input buffer " + p + "), because that input buffer has no suitable supply mechanism to begin with");
		}
		if (!Demo.quiet) System.out.println("Input buffer " + dname + ": at "+now+", supply slow down by factor="+d.magnitude+" until t=" + end);
		p.safety.transDelayFactorUntil.setValueUntil(d);
		
	    } else if (vd.size()>1) {
		throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	    }

    
	}
	
    }

    /** Indicates whether the node has been halted by a disruption */
    private Timed haltedUntil = new Timed();

    /** Slowdown factors for various stages, as may be caused by disruptions. */
    Timed prodDelayFactorUntil = new Timed(),
    /** Affects transportation from Prod to QA, and/or from QA to the consumer */
	transDelayFactorUntil = new Timed(),
	qaDelayFactorUntil = new Timed();
    
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

    /** @return the sum of the sizes of outstanding orders. This
	of course will be 0 if this Production unit is input-driven,
	rather than plan-driven */
    double sumNeedToSend() {
	double sum = 0;
	for(Order e: needToSend) sum += e.amount;
	return sum;
    }
 
    
    /** Tell this production unit to fill orders that some other
	unit has on file. This is used in SC2 ver. 2.* when this
	is a backup unit meant to substitute for another unit.
    */       
    void sharePlan(Production other) {
	needToSend = other.needToSend;
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

    /** This is set by the constructor if there is a contract
	negotiation delay which may delay execution of orders */
    private OrderDelay orderDelay = null;

    /** Consumers (or management offices) call this method when
	they want this production unit to manufacture some
	product for them. This call may result in an immediate
	adding of the requested amount to the production plan,
	or to the start of contract negotiation, which will
	cause the plan increment at some later point.
    */	
    public void request(Order order) {
	if (orderDelay==null)   doAddToPlan(order);
	else orderDelay.enter(order);
    }

    /** The actual addition to the plan is performed here. If necessary,
	this method also places MTO orders for ingredients.
     */
    void doAddToPlan(Order order) {
	if (order==null || order.amount<0) throw new IllegalArgumentException(getName() +".addToPlan(" + order+")");
	if (order.amount==0) return;

	everPlanned += order.amount;

	if (Demo.verbose) System.out.println("DEBUG: " +getName() + ", at "+now()+" added to plan "+ order.amount +", everPlanned=" + everPlanned);

	
	
	//	if (startPlan != null) x += startPlan;
	//startPlan = x;
	needToSend.add(order.copy()); // use a copy, to enable later subtractions

	for(int j=0; j<nin; j++) {	    
	    InputStore p = inputStore[j];
	    if (p.safety!=null) {
		boolean z = p.safety.placeMtoOrder(j, recipeMto, order.amount);
		//System.out.println("DEBUG: " +getName() + ", at "+now()+" mto from " + p.safety.getName() + " ="+z);
	    }
	}

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
    
    /** After a batch has been made, subtracts it from the plan. Orders
	are treated as filled in the FIFO order.
     */
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
		if (!Demo.quiet)  System.out.println("At t=" + now + ", Production unit "+ getName() +" increasing failure rate by " + r +", until " + d.end());
		prodStage().setFaultRateIncrease(r, d.end());
	    }

	    //-- Which disruption type affects which timer
	    Disruptions.Type[] types = {
		Disruptions.Type.Halt,
		Disruptions.Type.ProdDelayFactor,
		Disruptions.Type.TransDelayFactor,
		Disruptions.Type.QaDelayFactor,
		Disruptions.Type.On
	    };

	    Timed[] timers = {
		haltedUntil,
		prodDelayFactorUntil,
		transDelayFactorUntil,
		qaDelayFactorUntil,
		manualOnUntil,
	    };
    
	    for(int j=0; j<types.length; j++) {
		Disruptions.Type type = types[j];
		for(Disruption d: ((Demo)state).hasDisruptionToday(type, getName())) {
		    timers[j].setValueUntil(d);
		    if (Demo.verbose) {
			String msg = "At t=" + now + ", Production unit "+ getName() + " has " +
			    (type==Disruptions.Type.On?"command":"disruption")+
			    " '"+type+"', mag="+d.magnitude+", until " + d.end()
			    +"; timer=" + timers[j].report(now());
			System.out.println(msg);
		    }
		}
	    }

	    //	    boolean debug = !Demo.quiet && getName().equals("arrayLargeAssembly");
	    //if (!hasEnoughInputs()) {
	    //	if (debug) System.out.println("At t=" + now + ", "+ getName() + " production is starved. Input stores: " + reportInputs(true));
	    //	return;
	    //}


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

	<p>
	Since SC-3 ver  1.006, we don't print input buffer columns
	in the time series file of a Production node that has no input
	buffers of its own, but shares somebody else's buffers.
	(Otherwise, the daily numbers would show as 0 anyway, because
	they are cleared after printing; so what's the point?)
    */
    private void dailyChart() {

	double releasedAsOfToday =getReleased();

	double releasedToday = releasedAsOfToday - releasedAsOfYesterday;
	releasedAsOfYesterday = releasedAsOfToday;
	
	boolean showInputs = (usingInputsOf == null);
	int ni = (showInputs? inputStore.length : 0);
	
	double[] data = new double[2 + 2*ni];
	int k=0;
	data[k++] = releasedToday;
	data[k++] = sumNeedToSend(); // (startPlan==null)? 0 : startPlan;
	for(int j=0; j<ni; j++) {
	    data[k++] = inputStore[j].getContentAmount();
	    //data[k++] = inputStore[j].receivedTodayFromNormal;
	    //data[k++] = inputStore[j].receivedTodayFromMagic;

	    data[k++] = inputStore[j].receivedTodayFromNormal +
		inputStore[j].receivedTodayFromMagic;

	    inputStore[j].clearDailyStats();
	}
	try {
	    if (charter==null) 	prepareCharter();       
	    charter.print(data);
	} catch(IOException ex) {
	    ex.printStackTrace(System.err);
	    throw new IllegalArgumentException("IOException happened when saving data: " + ex);
	}
    }
    

    /** Can we produce odd lots? Normally we can; but, for arithmetic
	simplicity, proration is not allowed if some of the inputs
	have a different input:output ratio than 1:1.
     */
    private boolean canProrateLots() {
	//	for(double x: recipe.inBatchSizes) {
	//  if (x!=recipe.outBatchSize) return false;
	//}
	return true;
    }

    
    /** Tries to make a batch and put it into prodDelay (or its
	waiting queue), if resources are available
	@return true if a batch was made; false if not enough input resources
	was there to make one, or the current plan does not call for one

	FIXME: with noPlan, just set need = the smallest input size
    */
    public boolean mkBatch() {
		
	double now = state.schedule.getTime();

	
	if (!isOpenForBusiness(now)) {
	    //System.out.println(getName()+ ":" + now + " H");
	    return false;
	}

	double need=recipe.outBatchSize;
	if (!noPlan) {
	    need = Math.min( need, sumNeedToSend());
	}
	if (need <= 0) return false;

	//boolean prorate = !noPlan && (need < recipe.outBatchSize) &&   canProrateLots();

	boolean prorate = (need < recipe.outBatchSize);
	double ratio[] = {1,1};
	if (prorate) ratio = new double[] { need, recipe.outBatchSize};

	boolean debug = Demo.verbose;
	//	    (getName().equals("arrayLargeAssembly") || getName().equals("arraySmallAssembly"));


	if (!hasEnoughInputs(ratio)) {
	    if (debug) {
		System.out.println(getName()+ " is starved at t="+now+", ratio="+Util.joinNonBlank("/",ratio)+", inputs=" + 
				   reportInputs(true));
	    }
	    return false;
 	}
		
	Vector<Batch> usedBatches = new Vector<>();


	for(int j=0; j<nin; j++) {
	    
	    InputStore p = inputStore[j];
	    //System.out.println("mkBatch: Available ("+p.getTypicalProvided()+")=" + p.reportAvailable());

	    double ne = recipe.inBatchSizes[j];
	    if (prorate) ne = (ne * need) / recipe.outBatchSize;

	    String before = getName()+ " was fine at t="+now+", ratio="+Util.joinNonBlank("/",ratio)+", inputs=" + 
		reportInputs(true);
	    
	    try {
		Batch b = p.consumeOneBatch(ne);
		if (b!=null) usedBatches.add(b);    
	    }
	    catch (IllegalArgumentException ex) {
		System.out.println(before);
		System.out.println(getName()+ " did not think it was starved at t="+now+", ratio="+Util.joinNonBlank("/",ratio)+", inputs=" + 
				   reportInputs(true));
		throw(ex);
	    }
	}

	if (debug) System.out.println("At t=" + now + ", " + getName() + " starts a batch");// still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());


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

    /** Reports the state of the input buffers. Only buffers which
	are actually used in the manufacturing by this Production node
	are reported. This avoids duplicating the reports in those cases
	when several stages' buffers are shared (as with cellProd/cellAssembly
	in SC-3); however, double-reporting still happens when multiple
	prod units genuinely use material from the same buffers
	(as with large/small substrate prod).
     */
    private String reportInputs(boolean showBatchSize) {
	Vector<String> v= new Vector<>();
	for(int j=0; j<nin; j++) {
	    InputStore input = inputStore[j];
	    if (recipe.inBatchSizes[j]>0) {
		v.add( "\n\t" +input.report(showBatchSize));
	    }
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
	    prodStage().getEverReleased();
    }

    public String report() {
	String ba = outResource instanceof Entity? " ba": " u";

	String s = "[" + cname()+"."+getName();
	if (inputStore.length>0) {
	    s += "; stored inputs=("+ reportInputs() +")\n";
	} else {
	    s += ". ";
	}
	s += noPlan?
	    "No planning (driven by input)" :
	    "Ever planned: "+(long)everPlanned + "; still to do "+sumNeedToSend();
	s +=
	    ". Ever started: "+(long)everStarted + " ("+batchesStarted+" ba)";

	s += " = (in prod=" + prodStage().hasBatches() + ba +")";
	if (qaDelay!=null) {
	    if (needQa!=null) s += " (Waiting for QA=" + (long)needQa.getAvailable() +")";
	    s += "{QA: " + qaDelay.report() + "}";	    
	//s +="  in QA=" +   needQa.hasBatches() +")";
	} else {
	}


	
	s += "\n" + prodStage().report() + "\n";

	//if (stolenShipmentSink.getEverConsumed()>0) s+="\n" + stolenShipmentSink.report();
	if (everStolen>0) s+="\nLost in shipment " + everStolen;

	s+= ". Released " + getReleased();

	if (sm.outputSplitter !=null) 	s += "\n" + sm.outputSplitter.report();

	s+="]";
	return s;

    }


    /** Produces an optional Delay element that can be stuck at the output
	end of this Production unit. Configured based on the "outputDelay"
	field in the ParaSet. This element is not viewed as part of Production;
	rather, the higher level structure (Demo) may tack an output delay
	after the Production's output.
    */
    Delay mkOutputDelay(Receiver rcv)  throws IllegalInputException {


	AbstractDistribution distr = para.getDistribution("outputDelay", state.random); 
	CustomDelay delay = new CustomDelay(state, outResource);
	//delay.setDelayDistribution(distr);
	DelayRules dr = new DelayRules(distr, false, transDelayFactorUntil);
	delay.setDelayRules(dr);
	delay.setName("OutputDelay." + getName());

	//System.out.println("DEBUG: " + getName() + ", created " + delay.getName());
	
	delay.addReceiver(rcv);
	return delay;
    }

    /** This is set to non-null if this Production node does not
	have its own input buffers, but piggy-backs on somebody else's. */
    private Production usingInputsOf = null;

    /** Connect this Production unit with other elements of the supply chain,
	in accordance with the config parameters defining these links.
	This method should be called after all Production units have been
	created.
	
	<p>Examples:
	<pre>
	#-- 100% of the output goes to substrateProd.input[0]
	prepregProd,output,substrateProd,0,1.0
	</pre>

	<pre>
	substrateSmallProd.safety.prepreg,source,prepregProd
	</pre>
    */
    void linkUp(HashMap<String,Steppable> knownPools) throws IOException, IllegalInputException {
	Vector<String> output = para.get("output");
	//System.out.println("DEBUG: Linking " + getName());
	if (output != null) {
	    if (output.size()!=3) throw new  IllegalInputException("Expected 3 values for " + getName() + ",output");
	    String name = output.get(0);

	    final String suff = ".safety";
	    boolean thruSafety = name.endsWith(suff);
	    if (thruSafety) name = name.substring(0, name.length()-suff.length());
	    
	    Steppable _to =  knownPools.get(name);
	    if (_to==null) throw new  IllegalInputException("There is no unit named '" + name +"', in " + getName() + ",output");

	    Receiver r=null;
	    if (_to instanceof Production) {
		Production to = (Production)_to;
		int j = Integer.parseInt(output.get(1));
		InputStore dest0 = to.getEntrance(j);
		r = dest0;
		if (thruSafety) {
		    if (dest0.safety!=null) r = dest0.safety;
		    else System.out.println("Warning: ignoring the '.safety' suffix in " + getName() + ",output, because " + name + " has no safety stock");
		}
	    } else if (_to instanceof Receiver) {
		r = (Receiver)_to;
	    } else {
		throw new  IllegalInputException("There is no Receiver unit of any kind named '" + name +"', in " + getName() + ",output");
	    }
	    double f = Double.parseDouble(output.get(2));

	    if (Demo.verbose) System.out.println( "OUTER_LINK: " + getName() + " sends to "+ r.getName());

	    Receiver h = (para.get("outputDelay")!=null)? mkOutputDelay(r): r;
	    setQaReceiver(h, 1.0);	
	}


	for(int j=0; j<nin; j++) {
	    InputStore p = inputStore[j];
	    if (p.safety!=null) p.safety.linkUp(knownPools);
	}

	//-- Do we use some other production node's input buffers? 
	String name = para.getString("useInputsOf", null);
	if (name!=null) {	
	    Steppable other =  knownPools.get(name);
	    if (other!=null && other instanceof Production) {
		usingInputsOf = (Production)other;
		shareInputStore(usingInputsOf);
	    } else {
		throw new IllegalInputException(getName() + " cannot use input buffers of " + name + ", because the latter is not a Production node");
	    }
	}

	//-- the "replan" link in QA
	name = para.getString("replan", null);
	if (name!=null) {	
	    Steppable other =  knownPools.get(name);
	    if (other!=null && other instanceof Production) {
		if (qaDelay==null) throw new IllegalInputException(getName() + " does not need a 'replan' link, because it has no QA step and thus does not discard anything!");
		qaDelay.setReplan((Production)other);
	    } else {
		throw new IllegalInputException(getName() + " cannot trigger replan at " + name + ", because the latter is not a Production node");
	    }
	}

    }

    /** Initializes the time series headers for the Charter object.
	Since SC-3 ver. 1.006, the columns for input buffers
	are only printed in the Production object who owns these buffers,
	and not in any other Production objects that may share them.
     */
    private void prepareCharter() throws IOException {
	charter=new Charter(state.schedule, this);
	boolean showInputs = (usingInputsOf == null);
	int ni = (showInputs? inputStore.length : 0);
	
	String moreHeaders[] = new String[2 + 2*ni];
	int k = 0;
	moreHeaders[k++] = "releasedToday";
	moreHeaders[k++] = "outstandingPlan";
	for(int j=0; j< ni; j++) {
	    String rn = inputStore[j].getUnderlyingName();
	    moreHeaders[k++] = "Stock." + rn;
	    moreHeaders[k++] = "ReceivedToday." + rn;
	    //moreHeaders[k++] = "ReceivedTodayFromNormal." + rn;
	    //moreHeaders[k++] = "ReceivedTodayFromMagic." + rn;
	}
	charter.printHeader(moreHeaders);
    }

    
    /** This can be called when the channel is first created, so that we'll be ready to
	impose a "StopInfoFlow" disruption on it even before it's first use.
	This method is needed to implement BatchProvider2, even if its
	actual functionality does not matter much
    */
    public void registerChannel(Channel channel) {
	outChannels.add(channel);
    }

    
    
    /** Whither, and how, this Pool may send stuff. The list is compiled based on calls to feedTo() */
    Set<Channel> outChannels = new HashSet<>();
  
}
