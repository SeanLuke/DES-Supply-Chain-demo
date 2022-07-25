package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;

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
    implements Reporting,  Named
{

    /** A Queue for storing an input ingredient, with a facility
	to discard expired lots */
    class InputStore extends sim.des.Queue {
	/** Used to discard expired lots */
	Sink expiredDump;

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
	
	    sink = new MSink(state, getTypical());
	    // this is just for the purpose of the graphical display
	    addReceiver(sink);
	    addReceiver(expiredDump);

	}


	double discardedExpired=0;
	int discardedExpiredBatches=0;

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
	    } else throw new IllegalArgumentException("Wrong input resource type");
	}

	/** Purely for debugging */
	/*
	public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	    String given = (amount instanceof CountableResource)? ""+  amount.getAmount()+" units":
		(amount instanceof Batch)? "a batch of " + ((Batch)amount).getContentAmount() +" units":
		"an entity";
	    boolean z = super.accept(provider,  amount, atLeast,  atMost);
	    System.out.println("DEBUG: " + getName() + ", " +
			       (z? "accepted ": "rejected ") + 
			       given +	       			       "; has " +
			       (entities==null ? ""+getAvailable() + " units": ""+entities.size() + " ba") +
			       ";  totalReceivedResource=" +  getTotalReceivedResource()		       );
	    return z;
	}
	*/

	
    }

	

    /** Represents the storage of input materials (in Batches). They are already QA-tested by previous
	stages of the chain. These Queues are not scheduled; instead, Production.step() pulls
	stuff from them (by calling Queue.provide(..) when needed. 
    */
    InputStore[] inputStore;
    public sim.des.Queue[] getInputStore() { return inputStore;}

    private Charter charter;
 
    
    public static class ProdDelay extends Delay implements Reporting {
	/** Total batches started */
	int batchCnt=0;
	public int getBatchCnt() { return batchCnt; }
	/** Total pills started */
	double totalStarted=0;
        public double getTotalStarted() { return totalStarted; }

	ProdDelay(SimState state,       Batch resource) {
	    super(state, resource);
	}
	public boolean accept(Provider provider, Resource batch, double atLeast, double atMost) {
	    double amt = ((Batch)batch).getContentAmount();
	    //if (Demo.verbose) System.out.println("ProdDelay accepts batch of " + amt);
	    batchCnt++;
	    totalStarted+=amt;
	    return super.accept( provider, batch, atLeast, atMost);
	}
	public String hasBatches() {
	    String s = "" + (long)getDelayed();
	    if (getAvailable()>0) s += "+"+(long)getAvailable();
	    return s;
	}
	       
	public String report() {
	    return "[Production line ("+getTypical().getName()+"): accepted " +  batchCnt+" ba, totaling " + (long)totalStarted+"]";
	}
	    
    }
    
    ProdDelay prodDelay;
    /** Models the delay taken by the QA testing at the output	*/
    QaDelay qaDelay;


    public ProdDelay getProdDelay() { return prodDelay; }
    public QaDelay getQaDelay() { return qaDelay; }


    /** How many units of each input need to be taken to start cooking a batch? */
    final double[] inBatchSizes;
    /** How big is the output batch? */
    final double outBatchSize;

    /** The maximum number of batches that can be started each day */
    final int batchesPerDay;

    /** Where inputs come from */
    //final PreprocStorage[] preprocStore;
    //final sim.des.Queue postprocStore;


    /** What is the "entry point" for input No. j? */
    Receiver getEntrance(int j) {
	return inputStore[j];
    }


    final Batch outResource; 
    
    /** @param inResource Inputs (e.g. API and excipient). Each of them is either a Batch or CountableResource
	@param outResource batches of output (e.g. bulk drug)
     */

    Production(SimState state, String name, Config config,
	       Resource[] inResources,
	       Batch _outResource ) throws IllegalInputException, IOException
    {
	//super(state, outResource);
	outResource = _outResource;
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	//setCapacity(para.getDouble("capacity"));

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
	batchesPerDay = (int)para.getLong("batchesPerDay");

	qaDelay = QaDelay.mkQaDelay( para, state, outResource);
	if (this instanceof Macro)  addProvider(qaDelay, false);
 	
	prodDelay = new ProdDelay(state,outResource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",state.random));				       
	prodDelay.addReceiver(qaDelay);

	if (qaDelay.reworkProb >0) {
	    qaDelay.setRework( prodDelay);
	}

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
	macroField.add(qaDelay, x +=dx, y+=dy);
        macroField.connectAll();
	setField(macroField);

   }
    
    /** Sets the destination for the product that has passed the QA. This
	should be called after the constructor has returned, and before
	the simulation starts.
       @param _rcv The place to which good stuff goes after QA
     */
    void setQaReceiver(Receiver _rcv) {
	qaDelay.addReceiver( _rcv);
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
    

    /** Produce as many batches as allowed by the production capacity (per day)
	and available inputs.
    */
    public void step​(SimState state) {

	try {
	
	// FIXME: should stop working if the production plan has been fulfilled
	//double haveNow = getAvailable() + prodDelay.getDelayed() +	    qaDelay.getDelayed();

	    double now = state.schedule.getTime();
	    if (!hasEnoughInputs()) {
		if (Demo.verbose)  System.out.println("At t=" + now + ", Production of "+ prodDelay.getTypical()+" is starved. Input stores: " +
						      reportInputs(true));
		return;
	    }

	    for(int nb=0; nb<batchesPerDay && hasEnoughInputs(); nb++) {

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
	    
		if (Demo.verbose) System.out.println("At t=" + now + ", Production starts on a batch; still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());
		Batch onTheTruck = outResource.mkNewLot(outBatchSize, now, usedBatches);
		Provider provider = null;  // why do we need it?
		prodDelay.accept(provider, onTheTruck, 1, 1);
		batchesStarted++;
		everStarted += outBatchSize;
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
	    String s = input.getTypical().getName() +":" +  input.getAvailable();
	    s += " batches";
	    //if (showBatchSize) s += "/" + inBatchSizes[j];
	    if (input.discardedExpiredBatches>0) s += ". (Discarded expired=" + input.discardedExpired + " u = " + input.discardedExpiredBatches + " ba)";
	    
	    v.add(s);
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
	s += " = ("+
	    "in prod=" +   prodDelay.hasBatches() +
	    " ba; in QA= " +  qaDelay.hasBatches() +
	    " ba; discarded="+(long)qaDelay.badResource  +
	    " ("+qaDelay.badBatches+" ba)";
	if (qaDelay.reworkProb>0) s+= "; rework="+(long)qaDelay.reworkResource +
				      " ("+qaDelay.reworkBatches+" ba)";

	s += "; good=" + (long)qaDelay.releasedGoodResource+" ("+qaDelay.releasedBatches+" ba))]";
	s += "\n" + prodDelay.report();
	return s;

    }
  

    //String name;
    //    public String getName() { return name; }
    //    public void setName(String name) { this.name = name; }    
    //    public void reset(SimState state)     	{ }  //{ 	clear();    	}
}
