package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;
import edu.rutgers.pharma3.Disruptions.Disruption;

/** A Production plant receives QA-inspected ingredients pushed to it
    from upstream suppliers, puts them through production and QA
    delays, and pushes the output to a specified Receiver.

    <p>A Production object typically includes a ProdDelay (modeling
    the production step), an optional SimpleDelay modeling a
    transportaion delay, and a QaDelay modeling the testing stage. All
    three of them are actually "throttled delays", i.e. a combination
    of a ThrottleQueue (which holds batches to be processed) and a
    SimpleDelay (or a derived class object) which handles at most 1
    batch at a time.

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
public class Production extends sim.des.Macro
    implements Reporting,  Named, SplitManager.HasQA
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
    /** Models the delay taken by the QA testing at the output	*/
    private QaDelay qaDelay;
    
    final ProdThrottleQueue needProd;
    private final ThrottleQueue needTrans, needQa;


    public ProdDelay getProdDelay() { return prodDelay; }
    public QaDelay getQaDelay() { return qaDelay; }


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
    
    /** @param inResource Inputs (e.g. API and excipient). Each of them is either a (prototype) Batch or a CountableResource
	@param outResource batches of output (e.g. bulk drug)
     */

    Production(SimState state, String name, Config config,
	       Resource[] _inResources,
	       Batch _outResource ) throws IllegalInputException, IOException
    {
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
	if (this instanceof Macro)  addProvider(qaDelay, false);
	needQa =new ThrottleQueue(qaDelay, cap, para.getDistribution("qaDelay",state.random)); 

	if (para.get("transDelay")!=null) {
	    transDelay = new SimpleDelay(state, outResource);
	    transDelay.setName("TransDelay of " + outResource.getName());
	    needTrans = new ThrottleQueue(transDelay, cap, para.getDistribution("transDelay",state.random)); 
	    transDelay.addReceiver(needQa);
	} else {
	    transDelay = null;
	    needTrans = null;
	}
	
	prodDelay = new ProdDelay(state,outResource);
	prodDelay.addReceiver(needTrans!=null? needTrans: needQa);
	needProd = new ProdThrottleQueue(prodDelay, cap, para.getDistribution("prodDelay",state.random));
	
	if (qaDelay.reworkProb >0) {
	    qaDelay.setRework( needProd);
	}

	sm = new SplitManager(this, outResource, qaDelay);
	
	charter=new Charter(state.schedule, this);
	String moreHeaders[] = new String[2 + inResources.length];
	moreHeaders[0] = "releasedToday";
	moreHeaders[1] = "outstandingPlan";
	for(int j=0; j<inputStore.length; j++) {
	    moreHeaders[j+2] = "Stock." + inputStore[j].getUnderlyingName();
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
	macroField.add(qaDelay, x +=dx, y+=dy);
	if (sm.outputSplitter!=null) {
	    macroField.add(sm.outputSplitter, x += dx, y += dy);
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
	input resources. This is only done in FC, not CMO, as per
	Abhisekh's specs.
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
		double amt = vd.get(0).magnitude * 1e7;
		p.deplete(amt);			    
	    } else if (vd.size()>1) {
		throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	    }
    
	}
	
    }

    Timer haltedUntil = new Timer();
    
    /** Checks if there is a "Halt" disruption in effect for this unit. */
    private boolean isHalted(SimState state) {
	Vector<Disruption> vd = ((Demo)state).hasDisruptionToday(Disruptions.Type.Halt, getName());
	double now = state.schedule.getTime();
	for(Disruption d: vd) haltedUntil.enableUntil( now+d.magnitude );
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

	    /*
	    if (!hasEnoughInputs()) {
		for(int j=0; j<inBatchSizes.length; j++) {
		    boolean has =  inputStore[j].hasEnough(inBatchSizes[j]);
		    String msg = "Starvation report at t="+now+": for " + getName()+".input["+j+"], hasEnough("+inBatchSizes[j]+")=" + has;
		    if (!has) msg += ". Typical=" + inputStore[j].getTypical() +", avail="+inputStore[j].getAvailable();
		    System.out.println(msg);

		}
	    }
	    
	    */
	} finally {
	    dailyChart();
	}
	
    }

 
    //private static final boolean skipWork = false;
    
    /** Writes this days' time series values to the CSV file. 
	Does that for the safety stocks too, if they exist.
     */
    private void dailyChart() {
	//if (skipWork) return;
	
	    double releasedAsOfToday = qaDelay.getReleasedGoodResource();
	    double releasedToday = releasedAsOfToday - releasedAsOfYesterday;
	    releasedAsOfYesterday = releasedAsOfToday;

	    double[] data = new double[2 + inputStore.length];
	    data[0] = releasedToday;
	    data[1] = (startPlan==null)? 0 : startPlan;
	    for(int j=0; j<inputStore.length; j++) {
		data[j+2] = inputStore[j].getContentAmount();
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
    boolean mkBatch(SimState state) {

	if (startPlan != null && startPlan <= 0) return false;
	if (isHalted(state)) return false;
	if (!hasEnoughInputs()) return false;
	double now = state.schedule.getTime();
		
	Vector<Batch> usedBatches = new Vector<>();
	
	for(int j=0; j<inBatchSizes.length; j++) {
	    
	    InputStore p = inputStore[j];
	    //System.out.println("mkBatch: Available ("+p.getTypical()+")=" + p.reportAvailable());
	    Batch b = p.consumeOneBatch();
	    if (b!=null) 		usedBatches.add(b);

	    
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

    /** It's like ThrottleQueue, except that it does not actually hold much 
	product in, but "makes" it on the fly */
    class ProdThrottleQueue extends ThrottleQueue {
	public ProdThrottleQueue(SimpleDelay _delay, double cap, AbstractDistribution _delayDistribution) {
	    super( _delay, cap,  _delayDistribution);
	}

	/** This method ensures that whenever this queue is called
	    upon to provide a batch for the ProdDelay (via its slackProvider
	    mechanism) it will make itself non-empty, if at all possible. */
	public boolean provide(Receiver receiver) {
  	    if (getAvailable()==0) {
		mkBatch(getState());
	    }
	    return super.provide(receiver);
	}

	/*
	protected boolean offerReceiver(Receiver receiver, double atMost) {
	    if (getAvailable()==0) {
		mkBatch(getState());
	    }
	    return super.offerReceiver(receiver, atMost);
	}	
	*/
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
	return qaDelay.badResource;
    }


    public double getReleased() {
	return qaDelay.releasedGoodResource;
    }

    public String report() {
	
	String s = "[" + cname()+"."+getName()+"; stored inputs=("+ reportInputs() +"). "+
	    "Ever started: "+(long)everStarted + " ("+batchesStarted+" ba)";
	//if (qaDelay.reworkProb>0) s += " + (rework="+qaDelay.reworkResource+")";
	s += " = (in prod=" +   needProd.hasBatches() +	    " ba;";
	if (needTrans!=null) s +="  in trans=" +   needTrans.hasBatches() +")";
	s += " (Waiting for QA=" + (long)needQa.getAvailable() +")";
	s += " " + qaDelay.report();
	//s +="  in QA=" +   needQa.hasBatches() +")";
	    
	s += "\n" + prodDelay.report();


	if (sm.outputSplitter !=null) 	s += "\n" + sm.outputSplitter.report();
	
	return s;

    }

    //--------- Managing the downstream operations

    SplitManager sm;
    
    public void setQaReceiver(Receiver rcv, double fraction) {  
	sm.setQaReceiver(rcv, fraction);
    }
    
    /** Stats for planning */
    double[] computeABG() {
	return qaDelay.computeABG();
    }
    double computeGamma() {
	return computeABG()[2];
    }
}
