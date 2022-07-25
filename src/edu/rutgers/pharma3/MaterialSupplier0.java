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
public class MaterialSupplier0 extends Sink 
    implements  //Receiver,
			       Reporting
{

    private double outstandingOrderAmount=0;
    private double everOrdered=0;
    //private double badResource = 0, releasedGoodResource=0;

    //    private long everOrderedBatches=0;
    //private long badResourceBatches = 0, releasedGoodResourceBatches=0;
    private long startedProdBatches = 0;
    
    
    public double getOutstandingOrderAmount() { return outstandingOrderAmount; }
    public double getEverOrdered() { return everOrdered; }
    //public double getBadResource() { return badResource; }
    //public double getReleasedGoodResource() { return releasedGoodResource; }


 
 
    /** Production delay */
    private final Delay prodDelay;
    /** Transportation delay */
    private final Delay transDelay;
    private final QaDelay qaDelay;

    /** Similar to "typical", but with the storage array */
    private final Resource prototype;
    Resource getPrototype() { return prototype; }

    /** Creates a MaterialSupplier that will supply either a specified
	CountableResource or "batched resource" based on that resource.
	@param needLots If true, the supplier will supply lots 
     */
    static MaterialSupplier0 mkMaterialSupplier(SimState state, String name, Config config, 
					CountableResource resource, boolean needLots) 	throws IllegalInputException {
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	Resource proto = 
	    needLots?  Batch.mkPrototype(resource, config):
	    resource;
	return new   MaterialSupplier0( state, para, proto);
    }
	
    
    /** @param resource The product supplied by this supplier. Either a "prototype" Batch, or a CountableResource.
     */
    private MaterialSupplier0(SimState state, ParaSet para,
			     Resource resource	     ) 	throws IllegalInputException {
	super(state, resource);
	prototype = resource;

	setName(para.name);

	prodDelay = new Delay(state, resource);
	transDelay = new Delay(state, resource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",state.random));
	transDelay.setDelayDistribution(para.getDistribution("transDelay",state.random));
	
	qaDelay = QaDelay.mkQaDelay( para, state, resource);
	qaDelay.setWhomToWakeUp(this);
	

	prodDelay.addReceiver( this );
	transDelay.addReceiver( this );

	standardBatchSize = para.getDouble("batch");
	
    }

    final double standardBatchSize;

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
	qaDelay.addReceiver( rcv );
    }

    
    /** This method is called by an external customer when it needs
	this supplier to send out some amount of stuff. It "loads some
	stuff on a ship", i.e. puts it into the delay.
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
	if (prodDelay.getDelayed()>0) return false;
	if (outstandingOrderAmount<=0) return false;
	//double x = Math.min(outstandingOrderAmount ,  standardBatchSize);
	double x = standardBatchSize;
	Resource batch = (prototype instanceof Batch) ? ((Batch)prototype).mkNewLot(x, state.schedule.getTime()) :
	    new CountableResource((CountableResource)prototype, x);

	Provider provider = null;
	double t = state.schedule.getTime();
	if (Demo.verbose) System.out.println( "At t=" + t+", " + getName() + " putting batch of "+x+" into prodDelay");
	double a =batch.getAmount();
	boolean z = prodDelay.accept( provider, batch, a, a);
	if (!z) throw new AssertionError("prodDelay is supposed to accept everything, but it didn't!");
	double y = (batch instanceof Batch)? ((Batch)batch).getContentAmount() : ((CountableResource)batch).getAmount();
	outstandingOrderAmount -= y;

	startedProdBatches++;
	return true;
    }

    /** Starts the QA on the next batch that needs QA, if the QA 
	system is not busy. */
    private boolean startQaIfCan() {
	if (qaDelay.getDelayed()>0) return false;
	int n = 0;
	if (needQa.size()==0) return false;
	Resource batch = needQa.firstElement();
	needQa.removeElementAt(0);
	Provider provider = null;
	double t = state.schedule.getTime();
	double a =batch.getAmount();
	boolean z = qaDelay.accept( provider, batch, a, a);
	if (Demo.verbose) System.out.println( "At t=" + t+", " + getName() + " putting batch into qaDelay, z="+z +". qaDelay has " + qaDelay.getDelayed());
	return z;
    }


    /** Into this vector we put batches that have arrived from the
	transportation delay, until they can be put into the QA
	delay. */
    private Vector<Resource> needQa = new Vector<>();

    /** This is invoked in 3 different situations:
	when a ship comes in (stuff comes out of the 
	transportation delay), and the product goes thru QA.
	Removes some portion of the material as faulty, and send
	the rest to the designated receiver. 
	@param provider The Delay object representing the transportation delay
    */ 
    public boolean accept​(Provider provider, Resource resource, double atLeast, double atMost) {

	//Batch batch  = (Batch) resource;
	//CountableResource cr = batch.getContent();	
	double amt = (resource instanceof Batch)? ((Batch)resource).getContentAmount() : ((CountableResource)resource).getAmount();

	double t = state.schedule.getTime();
	boolean result=true;
	// atLeast=atMost = just a param for accept(); same as atLeast
	double lam = resource.getAmount();
	
	if (provider == prodDelay) {
	    // put the batch into the transDelay now
	    transDelay.accept(provider, resource, atLeast,  atMost);
	} else if (provider == transDelay) {
	    needQa.add(resource);	    
       
	} else throw new IllegalArgumentException("Unknown provider: " + provider);
	// schedule step() to be invoked very soon, but after the return of this method	
	state.schedule.scheduleOnce(t+ 1e-5, this);
	return result;
    }

    /** This functionality is taken outisde of accept() in order to
	avoid "cyclic offers" in the Delay objects.  (That is, the
	MaterialSupplier0.accept() triggered by the Delay must return
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
	    " ba. Of this, still in factory=" +  (long)prodDelay.getDelayed() +
	    " ba, on truck " + (long)transDelay.getDelayed() +
	    " ba, wait for QA " + needQa.size() +
	    " ba, in QA " + (long)qaDelay.getDelayed();
	if (transDelay.getAvailable()>0) s += "+" +  (long)transDelay.getAvailable();
	s += " ba. ";
	s += "QA discarded=" + qaDelay.badResource + " ("+qaDelay.badBatches+" ba)" +
	    ", QA released=" + qaDelay.releasedGoodResource + " ("+qaDelay.releasedBatches+" ba)";

	long missing = startedProdBatches - ((long)prodDelay.getDelayed() +
					     (long)transDelay.getDelayed() +
					     needQa.size() +
					     (long)qaDelay.getDelayed() +
					     qaDelay.badBatches+ qaDelay.releasedBatches);
	if ( prototype instanceof Batch &&     missing!=0) s += ". Missing " + missing + " ba";

	
	return wrap(s);
    } 


}
