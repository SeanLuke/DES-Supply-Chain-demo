package  edu.rutgers.pharma2;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

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
public class Production // extends sim.des.Queue
    implements Reporting,	       Steppable, Named
{


    /** Represents the storage of input materials. They are already QA-tested by previous
	stages of the chain. These Queues are not scheduled; instead, Production.step() pulls
	stuff from them (by calling Queue.provide(..) when needed
    */
    sim.des.Queue[] inputStore;

    static class ProdDelay extends Delay implements Reporting {
	int batchCnt=0;
	double totalStarted=0;
	ProdDelay(SimState state,       Resource resource) {
	    super(state, resource);
	}
	public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	    double amt = Math.min(((CountableResource)amount).getAmount(), atMost);
	    //if (Demo.verbose) System.out.println("ProdDelay accepts batch of " + amt);
	    batchCnt++;
	    totalStarted+=amt;
	    return super.accept( provider, amount, atLeast, atMost);
	}
	public String has() {
	    String s = "" + getTotal();
	    if (getAvailable()>0) s += "+"+getAvailable();
	    return s;
	}
	       
	public String report() {
	    return "[Production line ("+getTypical().getName()+"): accepted " +  batchCnt+" batches, totaling " + totalStarted+"]";
	}
	    
    }
    
    ProdDelay prodDelay;
    /** Models the delay taking by the QA testing at the output
	*/
    QaDelay qaDelay;

    /** How many units of each input need to be taken to start cooking a batch? */
    final double[] inBatchSizes;
    /** How big is the output batch? */
    final double outBatchSize;

    /** The maximum number of batches that can be started each day */
    final int batchesPerDay;

    /** Where inputs come from */
    //final PreprocStorage[] preprocStore;
    //final sim.des.Queue postprocStore;

    /** Dummy receivers used for the consumption of ingredients */
    final Sink[] sink;

    /** What is the "entry point" for input No. j? */
    Receiver getEntrance(int j) {
	return inputStore[j];
    }


    Production(SimState state, String name, Config config,
	       //PreprocStorage[] _preprocStore,
	       //sim.des.Queue _postprocStore,
	       Resource[] inResources,
	       Resource outResource ) throws IllegalInputException
    {
	//super(state, outResource);
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	//setCapacity(para.getDouble("capacity"));

	// Storage for input ingredients
	inputStore = new sim.des.Queue[inResources.length];
	for(int j=0; j<inputStore.length; j++) {
	    inputStore[j] = new sim.des.Queue(state,inResources[j]);
	    inputStore[j].setName("Input store for " + inResources[j].getName());
	}
	
	inBatchSizes = para.getDoubles("inBatch");
	if (inBatchSizes.length!=inputStore.length) throw new  IllegalInputException("Mismatch of the number of inputs: given " + inputStore.length + " resources, but " + inBatchSizes.length + " input batch sizes");

	outBatchSize = para.getDouble("batch");
	batchesPerDay = (int)para.getLong("batchesPerDay");
	
	qaDelay = new QaDelay(state, outResource, outBatchSize, para.getDistribution("faulty",state.random));
	qaDelay.setDelayDistribution(para.getDistribution("qaDelay",state.random));

	
	prodDelay = new ProdDelay(state,outResource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",state.random));				       
	prodDelay.addReceiver(qaDelay);

	Double reworkFraction = para.getDouble("rework", null);
	if (reworkFraction !=null) {
	    qaDelay.setReworkFraction(reworkFraction, prodDelay);
	}


	
	sink = new Sink[inputStore.length];
	for(int j=0; j<sink.length; j++) {
	    sink[j] = new MSink(state,inputStore[j].getTypical());
	}
	 
    }

    /** Sets the destination for the product that has passed the QA. This
	should be called after the constructor has returned, and before
	the simulation starts.
       @param _rcv The place to which good stuff goes after QA
     */
    void setQaReceiver(Receiver _rcv) {
	qaDelay.addReceiver( _rcv);
    }

    /** Do we have enough input materials of each kind to make a batch? */
    private boolean hasEnoughInputs() {
	for(int j=0; j<inBatchSizes.length; j++) {
	    if (inputStore[j].getAvailable()<inBatchSizes[j]) return false;
	}
	return true;
    }


    int batchesStarted=0;
    double everStarted = 0;

    public double getEverStarted() { return everStarted; }
    
    public void step​(SimState state) {
	// FIXME: should stop working if the production plan has been fulfilled
	//double haveNow = getAvailable() + prodDelay.getTotal() +	    qaDelay.getTotal();
	//if (haveNow  + outBatchSize < getCapacity() &&

	if (!hasEnoughInputs()) {
	    if (Demo.verbose)  System.out.println("At t=" + state.schedule.getTime() + ", Production of "+ prodDelay.getTypical()+" is starved. Input stores: " +
			       reportInputs(true));
	}
	
	for(int nb=0; nb<batchesPerDay && hasEnoughInputs(); nb++) {

	    for(int j=0; j<inBatchSizes.length; j++) {
		//inputStore[j].getResource().decrease(inBatchSizes[j]);
		double s0 =	inputStore[j].getAvailable();
		
//System.out.println("inputStore["+j+ "] has available="+
// s0+", does provide(sink, "+inBatchSizes[j]+")");
// inputStore[j].provide(sink[j], inBatchSizes[j]);
		double s1 =	inputStore[j].getAvailable();
		//System.out.println("At t=" + state.schedule.getTime() + ", Production took " + inBatchSizes[j] + " from " + inputStore[j].getName() + "; changed from " +s0 + " to " + s1);
	    }
	    
	    if (Demo.verbose) System.out.println("At t=" + state.schedule.getTime() + ", Production starts on a batch; still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getTotal()+"+"+prodDelay.getAvailable());
	    Resource onTheTruck = new CountableResource((CountableResource)qaDelay.getTypical(), outBatchSize);
	    Provider provider = null;  // why do we need it?
	    prodDelay.accept(provider, onTheTruck, outBatchSize, outBatchSize);
	    batchesStarted++;
	    everStarted += outBatchSize;
	}
		
	//  the Queue.step() call resource offers to registered receivers
	//super.step(state);
    }

    private String reportInputs(boolean showBatchSize) {
	Vector<String> v= new Vector<>();
	int j=0;
	for(sim.des.Queue input: inputStore) {	    
	    String s = input.getTypical().getName() +":" +  input.getAvailable();
	    if (showBatchSize) s += "/" + inBatchSizes[j];
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
	    "Ever started: "+everStarted;
	if (qaDelay.reworkFraction>0) s += " + (rework="+qaDelay.reworkResource+")";
	s += " = ("+
	    "in the works=" +   prodDelay.has() +
	    "; in QA= " +  qaDelay.has() +
	    "; discarded="+qaDelay.badResource;
	if (qaDelay.reworkFraction>0) s+= "; rework="+qaDelay.reworkResource;
	s += "; good=" + qaDelay.releasedGoodResource+")]";
	s += "\n" + prodDelay.report();
	return s;

    }

    String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }    
    public void reset(SimState state)     	{ }  //{ 	clear();    	}
}
