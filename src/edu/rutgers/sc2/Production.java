package  edu.rutgers.sc2;

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

    /** The buffers for inputs (raw materials) that go into production */
    private InputStore[] inputStore;
    public sim.des.Queue[] getInputStore() { return inputStore;}

    private Charter charter;
     
    ProdDelay prodDelay;

    /** Transportation from the production to the QA stage, as may
	exist in some tracks. It is null if transportation takes no time
	in the model.
    */
    private SimpleDelay transDelay = null;
    
    private final ThrottleQueue needProd;
    /** These only exist if the respective stages are throttled (FIFO,
	capacity-1), rather than "parallel" (infinite capacity) */
    private final ThrottleQueue needTrans, needQa;

    /** Returns true if the production step is empty, and one
	should see if it needs to be reloaded */
    boolean needsPriming() {
	return needProd!=null && 
	    needProd.getAvailable()==0 && prodDelay.getSize()==0;
    }
    
    /** If an external producer sends it product for us to do QA, this
	is where it should be sent */
    ThrottleQueue getNeedQa() { return needQa;}
    /** If an external producer sends it product for us to do QA, this
	is where it should be sent. This is either the QA delay itself (if
	parallel processing is allowed), or the waiting buffer (if throttled FIFO processing).
    */
    Receiver getQaEntrance() { return needQa!=null? needQa: qaDelay;}

    /** Where batches go for transportation. Either the transportation delay
	(if unlimited capacity) or the pre-transportation queue (if throttled)
     */
    Receiver getTransEntrance() { return needTrans!=null? needTrans: transDelay; }
	


    public ProdDelay getProdDelay() { return prodDelay; }

    /** Returns the last existing stage of this production unit. Typically
	this is the qaDelay, but some units (CMO Track A) don't have QA,
	so this will be the transportation delay, or even the production
	delay.  The main use of this method is so that we can add a Receiver
	to the Provider returned by this call, thus enabling this Production
	to send its product to the next element of the supply chain.
    */
    public Provider getTheLastStage() {
	return qaDelay!=null? qaDelay:
	    transDelay!=null? transDelay: prodDelay;
    }

   	
    /** How many units of each input need to be taken to start cooking a batch? */
    final double[] inBatchSizes;
    /** How big is the output batch? */
    final double outBatchSize;
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

    /** @param inResource Inputs (e.g. API and excipient). Each of them is either a (prototype) Batch or a CountableResource
	@param outResource batches of output (e.g. bulk drug)
     */    
    Production(SimState _state, String name, Config config,
	       Resource[] _inResources,
	       Batch _outResource ) throws IllegalInputException, IOException
    {
	state = _state;
	inResources =  _inResources;
	outResource = _outResource;
	setName(name);
	para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	inBatchSizes = (inResources.length==0)? new double[0]: para.getDoubles("inBatch");
	if (inBatchSizes.length!=inResources.length) throw new  IllegalInputException("Mismatch of the number of inputs for "+getName()+": given " + inResources.length + " resources ("+Util.joinNonBlank(";",inResources)+"), but " + inBatchSizes.length + " input batch sizes");


	// Storage for input ingredients
	inputStore = new InputStore[inResources.length];
	for(int j=0; j<inputStore.length; j++) {
	    inputStore[j] =
		new InputStore(this, state, config, inResources[j]);

	    if (this instanceof Macro) addReceiver(inputStore[j], false); 
	}
	
	outBatchSize = para.getDouble("batch");


	//-- Are trans and QA stages throttled (FIFO) or parallel?
	final boolean qaIsThrottled= para.getBoolean("qaThrottled", false),
	    transIsThrottled=para.getBoolean("transThrottled", false);
	
	//batchesPerDay = para.getLong("batchesPerDay", null);

	double cap = (outResource instanceof Batch) ? 1:    outBatchSize;	

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
	
	prodDelay = new ProdDelay(state,this,outResource);

	Receiver w = (getTransEntrance()!=null) ? getTransEntrance(): getQaEntrance();
	if (w!=null) prodDelay.addReceiver(w);

	AbstractDistribution d0 = para.getDistribution("prodDelay",state.random);
	AbstractDistribution dn = (d0==null)? null: new CombinationDistribution(d0, (int)outBatchSize);

	AbstractDistribution d = para.getDistribution("prodDelay",state.random);
	boolean unit = (d==null);
	if (unit)  d = para.getDistribution("prodDelayUnit",state.random);
	
	if (d!=null) {
	    needProd = new ThrottleQueue(prodDelay, cap, d, unit);

	    needProd.setWhose(this);
	    needProd.setAutoReloading(true);
	} else {
	    // Production delay is not specified; thus we assumed that
	    // production is (nearly) instant, as it's the case for
	    // RM EE supplier in SC-2. Therefore it's not throttled...
	    needProd  = null;
	    // A kludge for nearly-instant production
	    prodDelay.setDelayTime(0.0001);
	}
	
	if (qaDelay !=null && qaDelay.reworkProb >0) {
	    qaDelay.setRework( needProd!=null? needProd: prodDelay);
	}

	stolenShipmentSink = new MSink(state, outResource);
	stolenShipmentSink.setName("StolenShipmentsFrom." + getName());

    //System.out.println("DEBUG: in " + getName() +", " + getTheLastStage().getName() +			   " feeds to SM");

	
	sm = new SplitManager(this, outResource, getTheLastStage());

	charter=new Charter(state.schedule, this);
	//	String moreHeaders[] = new String[2 + 2*inResources.length];
	String moreHeaders[] = new String[2 + inResources.length];
	int k = 0;
	moreHeaders[k++] = "releasedToday";
	moreHeaders[k++] = "outstandingPlan";
	for(int j=0; j<inputStore.length; j++) {
	    moreHeaders[k++] = "Stock." + inputStore[j].getUnderlyingName();
	}
	//for(int j=0; j<inputStore.length; j++) {
	//    moreHeaders[k++] = "Anomaly." + inputStore[j].getUnderlyingName();
	//}
	charter.printHeader(moreHeaders);

    }

  /** Do we have enough input materials of each kind to make a batch? 
	FIXME: Here we have a simplifying assumption that all batches are same size. This will be wrong if the odd lots are allowed.
     */
    private boolean hasEnoughInputs() {
	for(int j=0; j<inBatchSizes.length; j++) {
	    if (!inputStore[j].hasEnough(inBatchSizes[j])) return false;
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
	
	for(int j=0; j<inBatchSizes.length; j++) {

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

    Timed haltedUntil = new Timed();    


    /** Checks if there is a "Halt" disruption in effect for this unit. */
    boolean isHalted(double now) {
	return haltedUntil.isOn( now );
    }

    /** If this is not null, it indicates how many units of the product we
	are still to produce (or, more precisely, to start). If null,
	then the control is entirely by the supply side. */
    Double startPlan = 0.0;

    /** Configure this unit to be controlled by the rationing of inputs */

    double everPlanned = 0;
    
    void setNoPlan() { startPlan = null; }
    //void setPlan(double x) { startPlan = x; }
    void addToPlan(double x) {
	if (x<0) throw new IllegalArgumentException(getName() +".addToPlan(" + x+")");
	everPlanned += x;
	if (startPlan != null) x += startPlan;
	startPlan = x;
    }

    private double everStolen=0;

    /** Destroys some shipments in the transportation delay (between production and QA) */
    private void disruptShipments(SimState state) {
	    Disruptions.Type type = Disruptions.Type.ShipmentLoss;
	    double now = state.schedule.getTime();
	    
	    for(Disruption d:  ((Demo)state).hasDisruptionToday(type, getName())) {
		int m = (int)d.magnitude;

		//for(int j=0; j<m && transDelay.getAvailable()>0; j++) {
		//  boolean z = transDelay.provide(stolenShipmentSink);
		//  if (!z) throw new AssertionError("Sink failed to accept");
		//}
		DelayNode[] nodes = transDelay.getDelayedResources();
		//int allCnt=0;
		int loseCnt=0, nowStolen=0;
		for(DelayNode node: nodes) {
		    if (node.isDead()) continue;
		    //allCnt++;
		    //System.out.println("DEBUG: Deleting shipment, size=" + Batch.getContentAmount(node.getResource()));
		    loseCnt++;
		    nowStolen += Batch.getContentAmount(node.getResource());
		    node.setDead(true);
		    if (loseCnt >= m) break;
		}
		everStolen += nowStolen;
		
		if (!Demo.quiet)  System.out.println("At t=" + now + ", Production unit "+ getName() +", disruption '"+type+"' could affect up to " + m + " shipments; actually deleted " + loseCnt +" ("+nowStolen+" u)");
		
	    }
	    
	
    }
    
      /** See if the production plan and the available inputs mandate the
	production of more batches, and if so, ensure that the prodDelay
	is 'primed' by needProd.

	A disruption may reduce the production capacity temporarily.
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
		prodDelay.setFaultRateIncrease(r, now+1);
	    }

	    Disruptions.Type type = Disruptions.Type.Halt;
	    for(Disruption d: ((Demo)state).hasDisruptionToday(type, getName())) { 
		haltedUntil.enableUntil( now+d.magnitude );
		if (!Demo.quiet)  System.out.println("At t=" + now + ", Production unit "+ getName() +" started disruption '"+type+"' until " + (now+d.magnitude));
	    }
   
	    
	    if (!hasEnoughInputs()) {
		if (Demo.verbose)
		    System.out.println("At t=" + now + ", "+ getName() + " production is starved. Input stores: " + reportInputs(true));
		return;
	    }

	    if (needProd!=null) {
		// This will "prime the system" by starting the first
		// mkBatch(), if needed and possible. After that, the
		// production cycle will repeat via the slackProvider
		// mechanism
		needProd.provide(prodDelay);
	    } else {
		int n = 0;
		while( mkBatch()) {
		    n++;
		}
		
		if (getName().equals("eeCmoProd")) {
		    System.out.println("At t=" + now + ", " + getName() +	" mkBatch done  "+n+ " batches");
		    //System.out.println("DEBUG: prodDelay=" + prodDelay.report0());
		    //System.out.println("DEBUG: transDelay=" + transDelay.report0());
		}
		

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
	double[] data = new double[2 + inputStore.length];
	int k=0;
	data[k++] = releasedToday;
	data[k++] = (startPlan==null)? 0 : startPlan;
	for(int j=0; j<inputStore.length; j++) {
	    data[k++] = inputStore[j].getContentAmount();
	}	
	
	//for(int j=0; j<inputStore.length; j++) {
	//    data[k++] = inputStore[j].detectAnomaly()? 1:0;
	//}
   
	charter.print(data);
		
	//for(InputStore p: inputStore) {
	//    if (p.safety!=null) p.safety.doChart(new double[0]);
	//}
    	
    }
    

    /** Can we produce odd lots? */
    private boolean canProrateLots() {
	for(double x: inBatchSizes) {
	    if (x!=outBatchSize) return false;
	}
	return true;
    }

    
    /** Tries to make a batch, if resources are available
	@return true if a batch was made; false if not enough input resources
	was there to make one, or the current plan does not call for one

    */
    public boolean mkBatch() {

	if (startPlan != null && startPlan <= 0) return false;

	double now = state.schedule.getTime();

	
	if (isHalted(now)) {
	    //System.out.println(getName()+ " H");
	    return false;
	}
	//System.out.println(getName()+ " W");
	if (!hasEnoughInputs()) return false;
		
	//Vector<Batch> usedBatches = new Vector<>();

	boolean prorate = (startPlan!=null) && (startPlan < outBatchSize) &&
	    canProrateLots();
	
	for(int j=0; j<inBatchSizes.length; j++) {
	    
	    InputStore p = inputStore[j];
	    //System.out.println("mkBatch: Available ("+p.getTypicalProvided()+")=" + p.reportAvailable());

	    double need = inBatchSizes[j];
	    if (prorate) need = (need * startPlan) / outBatchSize;
	    
	    Batch b = p.consumeOneBatch(need);
	    //if (c != inBatchSizes[j]) throw new IllegalArgumentException();
	    //if (b!=null) usedBatches.add(b);    
	}

	if (Demo.verbose) System.out.println("At t=" + now + ", " + getName() + " starts a batch; still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());


	double outAmt = (prorate)? startPlan: outBatchSize;
	Batch onTheTruck = outResource.mkNewLot(outAmt, now, null); //usedBatches);
	Provider provider = null;  // why do we need it?		
	(needProd!=null? needProd: prodDelay).accept(provider, onTheTruck, 1, 1);

	batchesStarted++;
	everStarted += outAmt;
	if (startPlan != null) startPlan -= outAmt;

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
	    prodDelay.getTotalStarted();
    }

    public String report() {
	String ba = outResource instanceof Entity? " ba": " u";

	String s = "[" + cname()+"."+getName();
	if (inputStore.length>0) {
	    s += "; stored inputs=("+ reportInputs() +")";
	}
	s += (startPlan==null) ?
	    ". No planning (driven by input)" :
	    ". Ever planned: "+(long)everPlanned + "; still to do "+startPlan;
	s +=
	    ". Ever started: "+(long)everStarted + " ("+batchesStarted+" ba)";

	if (needProd!=null) {
	    s += " = (in prod=" + needProd.hasBatches() +	    " ba;";
	} else s +=" (in prod=" + (long)prodDelay.getDelayedPlusAvailable() + ba + ")";

	if (needTrans!=null) s +=" (in trans=" +   needTrans.hasBatches() +")";
	else if (transDelay!=null) s +=" (in trans=" +   (long)transDelay.getDelayedPlusAvailable() + ba +")";
	if (qaDelay!=null) {
	    if (needQa!=null) s += " (Waiting for QA=" + (long)needQa.getAvailable() +")";
	    s += " " + qaDelay.report();	    
	//s +="  in QA=" +   needQa.hasBatches() +")";
	} else {
	}
	    
	s += "\n" + prodDelay.report();

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


    
