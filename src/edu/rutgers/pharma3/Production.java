package  edu.rutgers.pharma3;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;

/** A Production plant receives QA-inspected ingredients pushed to it
    from upstream suppliers, puts them through production and QA
    delays, and pushes the output to a specified Receiver.

    <p>A Production object typically includes a ProdDelay (modeling
    the production step), an optional SimpleDelay modeling a
    transportaion delay, and a QaDelay modeling the testing stage. In
    accordance with Ben's "forklift model", all three of them are
    actually "throttled delays", i.e. a combination of a ThrottleQueue
    (which holds batches to be processed) and a SimpleDelay (or a
    derived class object) which handles at most 1 batch at a time.

    <P>Production objects are used to represent the 3 stages of the FC
    operation (API production, drug production, and packaging), as
    well the the 4 parallel tracks of the CMO operation (A, B, C, D).
   
    <p>Sample usage:
    <ol>

    <li>Create a Production element: p=new Production(...);

    <li>Tell the Production element where to push the finished product:
    p.setQaReceiver(Receiver _rcv).

    <li>Ensure that upstream suppliers will push raw materials to each p.getEntrance(j)

    <li>Schedule the production element, so that it will know to initiate production daily.
    </ol>


  */
public class Production extends AbstractProduction
    implements Reporting,  Named
{

    /** Represents the storage of input materials (in Batches). They are already QA-tested by previous
	stages of the chain. These Queues are not scheduled; instead, Production.step() pulls
	stuff from them (by calling Queue.provide(..) when needed. 
    */
    private InputStore[] inputStore;
    public sim.des.Queue[] getInputStore() { return inputStore;}

    private Charter charter;
     
    ProdDelay prodDelay;
    /** Exists only in CMO tracks */
    private SimpleDelay transDelay = null;
    
    final ThrottleQueue needProd;
    private final ThrottleQueue needTrans, needQa;

    /** If an external producer sends it product for us to do QA, this
	is where it should be sent */
    ThrottleQueue getNeedQa() { return needQa;}
    

    public ProdDelay getProdDelay() { return prodDelay; }

    /** Returns the last existing stage of this production unit. Typically
	this is the qaDelay, but some units (CMO Track A) don't have QA. */
    public Provider getTheLastStage() {
	return qaDelay!=null? qaDelay:
	    transDelay!=null? transDelay: prodDelay;
    }

	
    /** How many units of each input need to be taken to start cooking a batch? */
    final double[] inBatchSizes;
    /** How big is the output batch? */
    final double outBatchSize;

    /** The maximum number of batches that can be started each day. If null, there is no 
	limit (other then the input resources)
     */
    final Long batchesPerDay;

    /** What is the "entry point" for input No. j? */
    Receiver getEntrance(int j) {
	return inputStore[j];
    }

    final Resource[] inResources;
    final Batch outResource; 
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

	inBatchSizes = para.getDoubles("inBatch");
	if (inBatchSizes.length!=inResources.length) throw new  IllegalInputException("Mismatch of the number of inputs for "+getName()+": given " + inResources.length + " resources ("+Util.joinNonBlank(";",inResources)+"), but " + inBatchSizes.length + " input batch sizes");


	// Storage for input ingredients
	inputStore = new InputStore[inResources.length];
	for(int j=0; j<inputStore.length; j++) {
	    inputStore[j] = new InputStore(this, state, config, inResources[j], inBatchSizes[j]);
	    if (this instanceof Macro)  addReceiver(inputStore[j], false); 
	}
	
	outBatchSize = para.getDouble("batch");

	
	batchesPerDay = para.getLong("batchesPerDay", null);

	double cap = (outResource instanceof Batch) ? 1:    outBatchSize;	

	qaDelay = QaDelay.mkQaDelay( para, state, outResource);
	if (qaDelay != null) {
	    if (this instanceof Macro)  addProvider(qaDelay, false);
	    needQa =new ThrottleQueue(qaDelay, cap, para.getDistribution("qaDelay",state.random));
	    needQa.setWhose(this);
	} else {
	    needQa = null;
	}

	if (para.get("transDelay")!=null) {
	    transDelay = new SimpleDelay(state, outResource);
	    transDelay.setName("TransDelay of " + outResource.getName());
	    needTrans = new ThrottleQueue(transDelay, cap, para.getDistribution("transDelay",state.random)); 
	    if (needQa!=null) transDelay.addReceiver(needQa);
	} else {
	    transDelay = null;
	    needTrans = null;
	}
	
	prodDelay = new ProdDelay(state,outResource);
	prodDelay.addReceiver(needTrans!=null? needTrans: needQa);
	needProd = new ThrottleQueue(prodDelay, cap, para.getDistribution("prodDelay",state.random));
	needProd.setWhose(this);
	needProd.setAutoReloading(true);
	
	if (qaDelay !=null && qaDelay.reworkProb >0) {
	    qaDelay.setRework( needProd);
	}

	sm = new SplitManager(this, outResource, getTheLastStage());

	charter=new Charter(state.schedule, this);
	String moreHeaders[] = new String[2 + 2*inResources.length];
	int k = 0;
	moreHeaders[k++] = "releasedToday";
	moreHeaders[k++] = "outstandingPlan";
	for(int j=0; j<inputStore.length; j++) {
	    moreHeaders[k++] = "Stock." + inputStore[j].getUnderlyingName();
	}
	for(int j=0; j<inputStore.length; j++) {
	    moreHeaders[k++] = "Anomaly." + inputStore[j].getUnderlyingName();
	}
	charter.printHeader(moreHeaders);
	 
    }

    /** Lay out the elements for display */
    void depict(DES2D field, int x0, int y0) {
	if (field==null) return;
	field.add(this, x0, y0);
	setImage("images/factory.png", true);

	DES2D macroField = new DES2D(300, 250);
	    
	int dx = 50, dy=40;
	x0 = y0 = 20;
	int x=x0, y=y0;
	
	for(int j=0; j<inputStore.length; j++) {
	    macroField.add(inputStore[j], x0, y0 + j*dy);
	    macroField.add(inputStore[j].sink, x0+dx-10, y0);
	    // Let's not show this one, to avoid visual clutter
	    //macroField.add(inputStore[j].expiredDump, x0+15, y0 + j*dy);
	}
	x += dx;
	
	macroField.add(prodDelay, x, y);
	if (transDelay!=null) {
	    macroField.add(transDelay, x +=dx, y+=dy);
	}
	if (qaDelay!=null) {
	    macroField.add(qaDelay, x +=dx, y+=dy);
	    if (sm.outputSplitter!=null) {
		macroField.add(sm.outputSplitter, x += dx, y += dy);
	    }
	}


	macroField.connectAll();
	setField(macroField);

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
	input resources. This is only done in FC, and not in CMO, as
	per Abhisekh's specs.
    */
    private void disruptInputs(SimState state) {
	if (getName().startsWith("Cmo")) return;

	double t = state.schedule.getTime();
	
	for(int j=0; j<inBatchSizes.length; j++) {
	    //Resource r = inResources[j];

	    InputStore p = inputStore[j];
	    String name = p.getUnderlyingName();
	    Vector<Disruption> vd = ((Demo)state).hasDisruptionToday(Disruptions.Type.Depletion, name);
	    if (vd.size()==1) {
		// deplete inventory
		double amt = Math.round(vd.get(0).magnitude * 1e7);
		p.deplete(amt);			    
	    } else if (vd.size()>1) {
		throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	    }
    
	}
	
    }

    Timed haltedUntil = new Timed();


    
    /** Checks if there is a "Halt" disruption in effect for this unit. */
    private boolean isHalted(double now) {
	return haltedUntil.isOn( now );
    }

    /** If this is not null, it indicates how many units of the product we
	are still to produce (or, more precisely, to start). If null,
	then the control is entirely by the supply side. */
    Double startPlan = null;

    void setPlan(double x) { startPlan = x; }
    void addToPlan(double x) {
	if (startPlan != null) x += startPlan;
	startPlan = x;
    }
    
    /** Produce as many batches as allowed by the production capacity (per day)
	and available inputs. A disruption may reduce the production capacity temporarily.
    */
    public void step(SimState state) {

	try {
	    disruptInputs( state);

	    double now = state.schedule.getTime();
	    
	    for(Disruption d:  ((Demo)state).hasDisruptionToday(Disruptions.Type.Adulteration, getName())) {
		
		// reduce quality of newly produced lots, in effect for 1 day
		prodDelay.setFaultRateIncrease(0.1 * d.magnitude, now+1);
	    }


	    for(Disruption d: ((Demo)state).hasDisruptionToday(Disruptions.Type.Halt, getName())) { 
		haltedUntil.enableUntil( now+d.magnitude );
	    }
   
	    
	    if (!hasEnoughInputs()) {
		if (Demo.verbose)
		    System.out.println("At t=" + now + ", Production of "+ prodDelay.getTypical()+" is starved. Input stores: " + reportInputs(true));
		return;
	    }

	    // This will "prime the system" by starting the first
	    // mkBatch(), if needed and possible. After that, the
	    // production cycle will repeat via the slackProvider
	    // mechanism
	    needProd.provide(prodDelay);

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
	
	double[] data = new double[2 + 2*inputStore.length];
	int k=0;
	data[k++] = releasedToday;
	data[k++] = (startPlan==null)? 0 : startPlan;
	for(int j=0; j<inputStore.length; j++) {
	    data[k++] = inputStore[j].getContentAmount();
	}	
	
	for(int j=0; j<inputStore.length; j++) {
	    data[k++] = inputStore[j].detectAnomaly()? 1:0;
	}
   
	charter.print(data);
		
	for(InputStore p: inputStore) {
	    if (p.safety!=null) p.safety.doChart(new double[0]);
	}
    	
    }
    
    
    /** Tries to make a batch, if resources are available
	@return true if a batch was made; false if not enough input resources
	was there to make one, or the current plan does not call for one

    */
    public boolean mkBatch() {

	if (startPlan != null && startPlan <= 0) return false;

	double now = state.schedule.getTime();

	
	if (isHalted(now)) {
	    //System.out.println("H");
	    return false;
	}
	//System.out.println("W");
	if (!hasEnoughInputs()) return false;
		
	Vector<Batch> usedBatches = new Vector<>();
	
	for(int j=0; j<inBatchSizes.length; j++) {
	    
	    InputStore p = inputStore[j];
	    //System.out.println("mkBatch: Available ("+p.getTypical()+")=" + p.reportAvailable());
	    Batch b = p.consumeOneBatch();
	    if (b!=null) usedBatches.add(b);    
	}

	if (Demo.verbose) System.out.println("At t=" + now + ", Production starts on a batch; still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());

	Batch onTheTruck = outResource.mkNewLot(outBatchSize, now, usedBatches);
	Provider provider = null;  // why do we need it?		
	needProd.accept(provider, onTheTruck, 1, 1);

	batchesStarted++;
	everStarted += outBatchSize;
	if (startPlan != null) startPlan -= outBatchSize;
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
	
	String s = "[" + cname()+"."+getName()+"; stored inputs=("+ reportInputs() +"). "+
	    "Ever started: "+(long)everStarted + " ("+batchesStarted+" ba)";

	s += " = (in prod=" +   needProd.hasBatches() +	    " ba;";
	if (needTrans!=null) s +="  in trans=" +   needTrans.hasBatches() +")";
	if (qaDelay!=null) {
	    s += " (Waiting for QA=" + (long)needQa.getAvailable() +")";
	    s += " " + qaDelay.report();	    
	//s +="  in QA=" +   needQa.hasBatches() +")";
	}
	    
	s += "\n" + prodDelay.report();


	if (sm.outputSplitter !=null) 	s += "\n" + sm.outputSplitter.report();
	
	return s;

    }

    /** Assuming that RM-API-BulkDrug-PackagedDrug is the main
	production chain, which input buffer is the "main" one for
	this production unit? This method is used as a help in simplified 
	analysis.
	
    */
    InputStore findTheMainInput(Batch[] theMainChainOfResources)  {
	for(int j=0; j<inputStore.length; j++) {
	    Resource r = inputStore[j].prototype;
	    for(Batch b:  theMainChainOfResources) {
		if (r==b) return  inputStore[j];
	    }
	}
	return null;

    }
    

    /** The main performance parameters of this Production node, for
	use in the overall system performance forecasting in 
	GraphAnalysis. 

	<p>
	FIXME: Same as in GraphAnalysis, the assumption here is that 
	the outBatchSize = the inBatchSize for the "main" ingredient,
	and there are no constraints for non-main ingredients.
    */
    static class Perfo {
	double alpha=0, beta=0;
	/** Output/input ratio of this node. Typically, it's computed
	    as (1-alpha-beta) / (1-beta), where alpha is the fault rate,
	    and beta is the rework rate.
	 */
	double gamma=1;
	/** How many units can go per day through the
	    production-transportation-QA chain? If this value is P,
	    then this Producion node can, on average, take up
	    (1-beta)*P units of the input material per day, and
	    release up to (1-alpha-beta)*P units.
	*/
	double thruput=0;
	/** Link to the Production node being analyzed */
	Production  production=null;
	boolean hasSafety=false;
	Perfo() {}
	Perfo(Production p, Batch[] theMainChainOfResources) {
	    production=p;
	    double[] abg = p.computeABG();
	    alpha = abg[0];
	    beta = abg[1];
	    gamma = abg[2];

	    InputStore input = p.findTheMainInput(theMainChainOfResources);
	    if (input==null) throw new IllegalArgumentException("Don't know what the main input is in p=" + p);
	    
	    
	    hasSafety = (input.safety!=null);
	    //thruput = p.outBatchSize * ParaSet.computeMean(p.needProd.getDelayDistribution());
	    if (input.batchSize != p.outBatchSize)  throw new IllegalArgumentException("Analysis not supported for different batch sizes;n p=" + p);
	    double d =  ParaSet.computeMean(p.needProd.getDelayDistribution());
	    if (p.needTrans!=null) {
		double d2 =  ParaSet.computeMean(p.needProd.getDelayDistribution());
		if (d2>d) d = d2;
	    }

	    if (p.needQa!=null) {
		double d2 =  ParaSet.computeMean(p.needQa.getDelayDistribution());
		if (d2>d) d = d2;
	    }
	    thruput = p.outBatchSize / d;
	}
    }

    
}
