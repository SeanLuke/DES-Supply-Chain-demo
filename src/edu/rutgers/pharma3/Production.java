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

/** A Production plant receives QA-inspected ingredients pushed to it from upstream
    suppliers, puts them through production and QA delays, and pushes the output 
    to a specified Receiver.

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

    /** A Queue for storing an input ingredient, with a facility
	to discard expired lots */
    class InputStore extends sim.des.Queue {
	/** Used to discard expired lots */
	Sink expiredDump;
	/** Simulates theft or destruction (disruption type A4 etc) */
	Sink stolenDump;

	/** Dummy receiver used for the consumption of this
	    ingredient as it's used, with metering */
	MSink sink;
	
	InputStore(SimState _state,
		   Resource resource) {
	    super(_state, resource);
	    String name;
	    //name = Production.this.getName() + "/Input store for " + resource.getName();
	    name = "Input("+resource.getName()+")";
	    setName(name);

	    setOffersImmediately(false); // the stuff sits here until taken
	    
	    expiredDump = new Sink(state, resource);
	    stolenDump = new Sink(state, resource);
	
	    sink = new MSink(state, getTypical());
	    // this is just for the purpose of the graphical display
	    addReceiver(sink);
	    addReceiver(expiredDump);

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

	Batch consumeOneBatch() {
	    Batch b=getFirst();			
	    if (!offerReceiver(sink, b)) throw new AssertionError("Sinks ought not refuse stuff!");
	    remove(b);
	    return b;
	}
	
	/** Do we have enough input materials of this kind to make a batch? 
	    While checking the amount, this method also discards expired lots.

	    FIXME: Here we have a simplifying assumption that all batches are same size. This will be wrong if the odd lots are allowed.
	*/
	private boolean hasEnough(double inBatchSize) {
	    if (getTypical() instanceof Batch) {
		double t = state.schedule.getTime();

		// Discard any expired batches
		Batch b; 
		while (getAvailable()>0 &&
		       (b=getFirst()).willExpireSoon(t, 0)) {

		    // System.out.println(getName() + ", has expired batch; created=" + b.getLot().manufacturingDate +", expires at="+b.getLot().expirationDate+"; now=" +t);
		    if (!offerReceiver( expiredDump, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		    remove(b);
		    discardedExpired += b.getContentAmount();
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
	private synchronized double deplete(double amt) {
	    double destroyed = 0;
	    if (getTypical() instanceof Batch) {
		while(destroyed<amt && getAvailable()>0) {
		    Batch b=getFirst();
		    if (!offerReceiver( stolenDump, b)) throw new AssertionError("Sinks ought not refuse stuff!");
		    remove(b);
		    destroyed += b.getContentAmount();
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
	
	/** Purely for debugging */
	public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	    String given = (amount instanceof CountableResource)? ""+  amount.getAmount()+" units":
		(amount instanceof Batch)? "a batch of " + ((Batch)amount).getContentAmount() +" units":
		"an entity";
	    boolean z = super.accept(provider,  amount, atLeast,  atMost);

	    if (!z) {
	    
	    System.out.println("DEBUG: " + getName() + ", " +
			       (z? "accepted ": "rejected ") + 
			       given +	       			       "; has " +
			       (entities==null ? ""+getAvailable() + " units": ""+entities.size() + " ba") +
			       ";  totalReceivedResource=" +  getTotalReceivedResource()		       );

	    System.out.println("cap=" + getCapacity()+"; r/o="+ getRefusesOffers());

	    }
	    
	    return z;
	}
	
	String report(boolean showBatchSize)  {
	    String s = getTypical().getName() +":" +
		(getTypical() instanceof Batch? 
		 getAvailable() + " ba" :
		 getAvailable() + " u" );
        
	    //if (showBatchSize) s += "/" + inBatchSizes[j];
	    if (discardedExpiredBatches>0) s += ". (Discarded expired=" + discardedExpired + " u = " + discardedExpiredBatches + " ba)";
	    if (stolen>0) s += ". (Stolen=" + stolen+ " u = " + stolenBatches + " ba)";
	    return s;
	}

	
    }

	

    /** Represents the storage of input materials (in Batches). They are already QA-tested by previous
	stages of the chain. These Queues are not scheduled; instead, Production.step() pulls
	stuff from them (by calling Queue.provide(..) when needed. 
    */
    private InputStore[] inputStore;
    public sim.des.Queue[] getInputStore() { return inputStore;}

    private Charter charter;
 
    
    private ProdDelay prodDelay;
    /** Exists only in CMO tracks */
    private SimpleDelay transDelay = null;
    /** Models the delay taken by the QA testing at the output	*/
    private QaDelay qaDelay;
    
    private final ThrottleQueue needProd, needTrans,	needQa;


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
    
    /** @param inResource Inputs (e.g. API and excipient). Each of them is either a (prototype) Batch or a CountableResource
	@param outResource batches of output (e.g. bulk drug)
     */

    Production(SimState state, String name, Config config,
	       Resource[] _inResources,
	       Batch _outResource ) throws IllegalInputException, IOException
    {
	//super(state, outResource);
	inResources =  _inResources;
	outResource = _outResource;
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	// Storage for input ingredients
	inputStore = new InputStore[inResources.length];
	for(int j=0; j<inputStore.length; j++) {
	    inputStore[j] = new InputStore(state,inResources[j]);
	    //inputStore[j].setName(getName() + "/Input store for " + inResources[j].getName());
	    if (this instanceof Macro)  addReceiver(inputStore[j], false); 
	}
	
	inBatchSizes = para.getDoubles("inBatch");
	if (inBatchSizes.length!=inputStore.length) throw new  IllegalInputException("Mismatch of the number of inputs for "+getName()+": given " + inputStore.length + " resources ("+Util.joinNonBlank(";",inputStore)+"), but " + inBatchSizes.length + " input batch sizes");

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
	needProd = new ThrottleQueue(prodDelay, cap, para.getDistribution("prodDelay",state.random));
	
	if (qaDelay.reworkProb >0) {
	    qaDelay.setRework( needProd);
	}

	sm = new SplitManager(this, outResource, qaDelay);

	
	charter=new Charter(state.schedule, this);
 		 
    }

    /** Lay out the elements for display */
    void depict(DES2D field, int x0, int y0) {

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
	    Resource r = inResources[j];
	    String name = (r instanceof Batch)? ((Batch)r).getUnderlyingName(): r.getName();
	    InputStore p = inputStore[j];

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

    
    /** Produce as many batches as allowed by the production capacity (per day)
	and available inputs. A disruption may reduce the production capacity temporarily.
    */
    public void step​(SimState state) {

	try {

	    disruptInputs( state);
	    if (isHalted(state)) return;

	    double now = state.schedule.getTime();
	    
	    for(Disruption d:  ((Demo)state).hasDisruptionToday(Disruptions.Type.Adulteration, getName())) {
		
		// reduce quality of newly produced lots, in effect for 1 day
		prodDelay.setFaultRateIncrease(0.1 * d.magnitude, now+1);
	    }

	    
	// FIXME: should stop working if the production plan has been fulfilled
	//double haveNow = getAvailable() + prodDelay.getDelayed() +	    qaDelay.getDelayed();


	    if (!hasEnoughInputs()) {
		if (Demo.verbose)  System.out.println("At t=" + now + ", Production of "+ prodDelay.getTypical()+" is starved. Input stores: " +
						      reportInputs(true));
		return;
	    }
	    

	    int nb=0;
	    for(
		    ; (batchesPerDay==null || nb<batchesPerDay) && hasEnoughInputs(); nb++) {

		Vector<Batch> usedBatches = new Vector<>();
		
		for(int j=0; j<inBatchSizes.length; j++) {
		    
		    InputStore p = inputStore[j];
		    //System.out.println("Available ("+p.getTypical()+")=" + p.getAvailable());
		    if (p.getTypical() instanceof Batch) {
			//z = p.provide(p.sink, 1);
			
			usedBatches.add(p.consumeOneBatch());
		       			
		    } else if (p.getTypical() instanceof CountableResource) {
			boolean z = p.provide(p.sink, inBatchSizes[j]);				    if (!z) throw new IllegalArgumentException("Broken sink? Accept() fails!");    
		    } else throw new IllegalArgumentException("Wrong input resource type");

		    if (p.sink.lastConsumed != inBatchSizes[j]) {
			String msg = "Batch size mismatch on sink["+j+"]=" +
			    p.sink +": have " + p.sink.lastConsumed+", expected " + inBatchSizes[j];
			throw new IllegalArgumentException(msg);
		    }
		    
		}
	    
		if (Demo.verbose)
		    System.out.println("At t=" + now + ", Production starts on a batch; still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());
		Batch onTheTruck = outResource.mkNewLot(outBatchSize, now, usedBatches);
		Provider provider = null;  // why do we need it?		
		needProd.accept(provider, onTheTruck, 1, 1);
		batchesStarted++;
		everStarted += outBatchSize;
	    }

	    
	    System.out.println("At " + now +", done step for " + getName()+". nb="+ nb +
			       ", batchesPerDay=" + batchesPerDay +
			       ", hasEnoughInputs="  + hasEnoughInputs());

	    if (!hasEnoughInputs()) {
		for(int j=0; j<inBatchSizes.length; j++) {
		    boolean has =  inputStore[j].hasEnough(inBatchSizes[j]);
		    String msg = "For " + getName()+".input["+j+"], hasEnough("+inBatchSizes[j]+")=" + has;
		    if (!has) msg += ". Typical=" + inputStore[j].getTypical() +", avail="+inputStore[j].getAvailable();
		    System.out.println(msg);

		}
	    }
	    
	    
	    //  the Queue.step() call resource offers to registered receivers
	    //super.step(state);

	} finally {
	    double releasedAsOfToday = qaDelay.getReleasedGoodResource();
	    double releasedToday = releasedAsOfToday - releasedAsOfYesterday;
	    releasedAsOfYesterday = releasedAsOfToday;
	    charter.print(releasedToday);
	}
	
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
	if (qaDelay.reworkProb>0) s += " + (rework="+qaDelay.reworkResource+")";
	s += " = (in prod=" +   needProd.hasBatches() +	    " ba;";
	if (needTrans!=null) s +="  in trans=" +   needTrans.hasBatches() +")";
	s +="  in QA=" +   needQa.hasBatches() +")";
	    
	s += "\n" + prodDelay.report();


	if (sm.outputSplitter !=null) 	s += "\n" + sm.outputSplitter.report();
	
	return s;

    }

    //--------- Managing the downstream operations

    SplitManager sm;
    
    public void setQaReceiver(Receiver rcv, double fraction) {  
	sm.setQaReceiver(rcv, fraction);
    }
    
    //String name;
    //    public String getName() { return name; }
    //    public void setName(String name) { this.name = name; }    
    //    public void reset(SimState state)     	{ }  //{ 	clear();    	}
}
