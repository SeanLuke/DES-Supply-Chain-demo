package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models a facility that supplies a raw material, plus the pharma co's 
    quality testing stage that handles the material from this supplier.

    <p>The Receiever (Sink) feature of this class is used so that we
    can use it "accept" batches coming out of the 3 stages of delays
    (prod, transportation, QA).

    <p>A MaterialSupplier does not need to be scheduled. Rather, a controlling
    element (company headquearters) needs to do 2 things:
<ol>

<li>At the beginning, call setQaReceiver(Receiver rcv), to specify the
object to which this supplier should push QA'ed material.

<li>Call receiveOrder(double amt) whenever it wants to it order some
 amount of the material. Internally, this will push some stuff into a
 delay, which will later trigger rcv.accept() on the downstream
 receiever. </ol>

 */
public class MaterialSupplier extends Sink 
    implements // Receiver,
			       Reporting
{

    private double outstandingOrderAmount=0;
    private double everOrdered=0;
    private double badResource = 0, releasedGoodResource=0;

    //    private long everOrderedBatches=0;
    private long badResourceBatches = 0, releasedGoodResourceBatches=0;
    private long startedProdBatches = 0;
    
    
    public double getOutstandingOrderAmount() { return outstandingOrderAmount; }
    public double getEverOrdered() { return everOrdered; }
    public double getBadResource() { return badResource; }
    public double getReleasedGoodResource() { return releasedGoodResource; }

 
    /** Production delay */
    private final Delay prodDelay;
    /** Transportation delay */
    private final Delay transDelay;
    private final Delay qaDelay;

    /** Similar to "typical", but with the storage array */
    private final Batch prototype;
    
    /** @param resource a "prototype" batch
     */
    MaterialSupplier(SimState state, String name, Config config, //Resource resource
		     Batch resource
		     ) 
	throws IllegalInputException {
	super(state, resource);
	prototype = resource;

	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");

	prodDelay = new Delay(state, resource);
	transDelay = new Delay(state, resource);
	qaDelay = new Delay(state, resource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",state.random));
	transDelay.setDelayDistribution(para.getDistribution("transDelay",state.random));
	qaDelay.setDelayDistribution(para.getDistribution("qaDelay",state.random));

	prodDelay.addReceiver( this );
	transDelay.addReceiver( this );
	qaDelay.addReceiver( this );

	faultyProbability = para.getDouble("faulty");

	standardBatchSize = para.getDouble("batch");
	
    }

    final double standardBatchSize, faultyProbability;

    /** The place to which good stuff goes after QA, e.g. a manufacturing
	plant.
    */
    // FIXME: an array of receivers can be used instead, with its own
    private Receiver rcv;
    
    /** Sets the destination for the product that has passed the QA. This
	should be called after the constructor has returned, and before
	the simulation starts.
       @param _rcv The place to which good stuff goes after QA
     */
    void setQaReceiver(Receiver _rcv) {
	rcv = _rcv;
    }



    
    /** This method is called by an external customer
	when it needs this supplier to send out some
	amount of stuff. It "loads some stuff on a ship",
	i.e. puts it into the delay.
    */
    void receiveOrder(double amt) {
	outstandingOrderAmount += amt;
	everOrdered += amt;
	startProductionIfCan();
    }

    /** Initiate the production process on a new batch, if the prod
	system is not busy.
	FIXME: we always use the standard batch size, and don't use 
	the last short batch, in order to simplify Production.
    */
    private boolean startProductionIfCan() {
	if (prodDelay.getTotal()>0) return false;
	if (outstandingOrderAmount<=0) return false;
	//double x = Math.min(outstandingOrderAmount ,  standardBatchSize);
	double x = standardBatchSize;
	Batch batch = prototype.mkNewLot(x);
	Provider provider = null;
	double t = state.schedule.getTime();
	System.out.println( "At t=" + t+", " + getName() + " putting batch into prodDelay");
	prodDelay.accept( provider, batch, 1,1);
	outstandingOrderAmount -= batch.getContentAmount();

	startedProdBatches++;
	return true;
    }

    /** Starts the QA on the text batch that needs QA, if the QA 
	system is not busy. */
    private boolean startQaIfCan() {
	if (qaDelay.getTotal()>0) return false;
	int n = 0;
	if (needQa.size()==0) return false;
	Batch batch = needQa.firstElement();
	needQa.removeElementAt(0);
	Provider provider = null;
	qaDelay.accept( provider, batch, 1,1);
	return true;
    }


    /** Into this vector we put batches that have arrived from the
	transportation delay, until they can be put into the QA
	delay. */
    private Vector<Batch> needQa = new Vector<>();

    /** This is invoked in 3 different situations:
when a ship comes in (stuff comes out of the 
	transportation delay), and the product goes thru QA.
	Removes some portion of the material as faulty, and send
	the rest to the designated receiver. 
	@param provider The Delay object representing the transportation delay
    */ 
    public boolean accept​(Provider provider, Resource resource, double atLeast, double atMost) {

	Batch batch  = (Batch) resource;
	CountableResource cr = batch.getContent();
	double amt = batch.getContentAmount();

	double t = state.schedule.getTime();
	boolean result=true;
	
	if (provider == prodDelay) {

	    System.out.println( "At t=" + t+", " + getName() + " getting batch from prodDelay");
	    
	    // put the batch into the transDelay now
	    transDelay.accept(provider, batch, 1,1);
	} else if (provider == transDelay) {
	    System.out.println( "At t=" + t+", " + getName() + " getting batch from transDelay");
	    needQa.add(batch);	    
	} else if (provider == qaDelay) {
	    System.out.println( "At t=" + t+", " + getName() + " getting batch from qaDelay");
	    boolean isFaulty = state.random.nextBoolean( faultyProbability);
	    if (isFaulty) {
		badResource += amt;
		badResourceBatches ++;
	    } else {
		releasedGoodResource += amt;
		releasedGoodResourceBatches++;

		if (rcv==null) {
		    System.out.println("Warning: " +  cname() + "."+getName() + " has no receiver set!");
		} else {
		    result= rcv.accept(provider, resource, 1,1);
		}
	    }
	} else throw new IllegalArgumentException("Unknown provider: " + provider);
	// schedule step() to be invoked very soon, but after the return of this method	
	state.schedule.scheduleOnce(t+ 1e-5, this);
	return result;
    }

    /** This functionality is taken outisde of accept() in order to
	avoid "cyclic offers" in the Delay objects.  (That is, the
	MaterialSupply.accept() triggered by the Delay must return
	before the next batch can be put into that Delay). This method is
	scheduled in accept() whenever a batch comes out of prodDelay
	or qaDelay, indicating that we may be able to put the next
	batch into those delays.
     */
    public void step​(sim.engine.SimState state) {	
	//double t = state.schedule.getTime();
	// Start production on the next batch, if needed
	startProductionIfCan();
	startQaIfCan();
    }

    
    public String report() {
	String s =// "[" + cname()
	    //+ "."+getName()+ "("+getTypical().getName()+")" +
	    "Ever ordered="+everOrdered+
	    "; ever started production="+	startedProdBatches+
	    " ba. Of this, still in factory=" +  (long)prodDelay.getTotal() +
	    " ba, on truck " + (long)transDelay.getTotal() +
	    " ba, wait for QA " + needQa.size() +
	    " ba, in QA " + (long)qaDelay.getTotal();
	if (transDelay.getAvailable()>0) s += "+" +  (long)transDelay.getAvailable();
	s += " ba. ";
	s += "QA discarded=" + badResource + " ("+badResourceBatches+" ba)" +
	    ", QA released=" + releasedGoodResource + " ("+releasedGoodResourceBatches+" ba)";

	long missing = startedProdBatches - ((long)prodDelay.getTotal() +
					     (long)transDelay.getTotal() +
					     needQa.size() +
					     (long)qaDelay.getTotal() +
					     badResourceBatches+ releasedGoodResourceBatches);
	if (missing!=0) s += ". Missing " + missing + " ba";

	
	return wrap(s);
    } 


}
