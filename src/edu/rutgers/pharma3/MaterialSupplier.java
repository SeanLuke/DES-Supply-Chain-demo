package  edu.rutgers.pharma3;

import edu.rutgers.supply.*;

import java.util.Vector;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;

/** Models a facility that supplies a raw material (i.e. an external
    supplier), plus the pharma co's quality testing stage that handles
    the material from this supplier.

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
public class MaterialSupplier extends AbstractProduction
    implements     Named,	    Reporting
{

    /** The number of units whose production we are ordered to start.
	(The number of units that will successfully emerge from QA will
	be smaller, so the FC is encouraged to over-order)
     */
    private double outstandingOrderAmount=0;
    private double everOrdered=0;
    private long startedProdBatches = 0;
    
    
    public double getOutstandingOrderAmount() { return outstandingOrderAmount; }
    public double getEverOrdered() { return everOrdered; }
 
 
    /** Production delay */
    private final ProdDelay prodDelay;
    /** Transportation delay */
    private final SimpleDelay transDelay;

    private final ThrottleQueue needProd, needTrans, needQa;

    /** Accepts resources when it's enabled, and rejects otherwise. This can be 
	used to imitate a temporary phenomenon, such as disappearance of loads
	sent on certain days.

	FIXME: this could also have been implemented with Sink.setRefuseOffers()
     */
    static class SometimesSink extends MSink {
	/** Will be accepting resource until this time
	    point (not inclusive) */
	Timed onUntil = new Timed();
	public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	    return
		onUntil.isOn(state.schedule.getTime()) &&
		super.accept(provider,  resource,  atLeast, atMost);
	}
	public SometimesSink(SimState state, Resource typical) {
	    super(state,typical);
	}
    }
    
    /** Used for disappared (stolen) lots in one of the disruptions */
    final SometimesSink stolenGoodsSink;
    
        
    /** Similar to "typical", but with the storage array */
    private final Resource prototype;
    Resource getPrototype() { return prototype; }

    /** Creates a MaterialSupplier that will supply either a specified
	CountableResource or "batched resource" based on that resource.

	@param name The name to be given to this supplier unit. It is used for reporting, and for looking up parameters in the config file.

	@param config ... in which the parameters will be looked up.

	@param a CountableResource describing the product to be produced by this supplier. If needLots is true, this will be packaged into a Batch resource (an Entity), so that individual lot numbers and expiration dates could be handled; if false, we will simply generate this CountableResource.

	@param needLots If true, the supplier will supply lots (Batch objects), so that an expiration date can be attached to each one. If false, the supplier will supply a CountableResource (modeling an absoulutely fungible product, with no expiration date).
      
     */
    static MaterialSupplier mkMaterialSupplier(SimState state, String name, Config config, 
					       CountableResource resource, boolean needLots) 	throws IllegalInputException, IOException {
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	Resource proto = 
	    needLots?  Batch.mkPrototype(resource, config):
	    resource;
	return new MaterialSupplier( state, para, proto);
    }
	
    protected SimState state;


    /** The tool to write a CSV file with data that can later be charted by an external program */	
    private Charter charter;

    //private static boolean throttleTrans = false;

    /** The standard one. This can be modified during disruptions */
    final private  AbstractDistribution transDelayDistribution;


    final ParaSet para;
    
    /** @param resource The product supplied by this supplier. Either a "prototype" Batch, or a CountableResource.
     */
    private MaterialSupplier(SimState _state, ParaSet _para,
			     Resource resource	     ) 	throws IllegalInputException, IOException {
	//super(state, resource);
	state = _state;
	para = _para;
	prototype = resource;

	setName(para.name);
	if (Demo.verbose) System.out.println("MS " + getName()+", proto=" +prototype);

	standardBatchSize = para.getDouble("batch");

	double cap = (prototype instanceof Batch) ? 1:    standardBatchSize;	

	prodDelay = new ProdDelay(state, resource);

	needProd = new ThrottleQueue(prodDelay, cap, para.getDistribution("prodDelay",state.random));
	needProd.setWhose(this);
	needProd.setAutoReloading(true);

	stolenGoodsSink = new SometimesSink(state, resource);

	transDelay = new SimpleDelay(state, resource);
	needTrans = new ThrottleQueue(transDelay, cap,
				      transDelayDistribution = para.getDistribution("transDelay",state.random));

	
	qaDelay = QaDelay.mkQaDelay( para, state, resource);
	//needQa = controlInput(qaDelay, cap);
	needQa =new ThrottleQueue(qaDelay, cap, para.getDistribution("qaDelay",state.random)); 
		
	//--- link them all	
	prodDelay.setOfferPolicy(Provider.OFFER_POLICY_FORWARD);
	prodDelay.addReceiver( stolenGoodsSink );
	prodDelay.addReceiver( needTrans );
	transDelay.addReceiver( needQa );
	//-- the output for qaDelay will be added by setQaReceiver
	sm = new SplitManager(this, resource, qaDelay);

	if (this instanceof Macro)  addProvider(qaDelay, false);
 
	charter=new Charter(state.schedule, this);
 	
    }

    /** Lays out icons for the GUI display of a product flow chart.

	(For custome icons, SVG won't work; only PNG is OK).

	@param x0 X offset for the top left corner
	@param y0 Y offset for the top left corner
    */
    void depict(DES2D field, int x0, int y0) {
	if (field==null) return;
	field.add(this, x0, y0);
	//this.setImage("images/square.svg", true);	    

	// lay out the flowchart pop up window 
	DES2D macroField = new DES2D(300, 250);
	int dx = 50, dy=40;
	int x=20, y=20;
	macroField.add(needProd, x, y);
	macroField.add(prodDelay, x += dx, y += dy);
	macroField.add(needTrans,  x += dx, y += dy);
	macroField.add(transDelay, x += dx, y += dy);
	macroField.add(needQa,  x += dx, y += dy);
	macroField.add(qaDelay, x += dx, y += dy);

	if (sm.outputSplitter!=null) {
	    macroField.add(sm.outputSplitter, x += dx, y += dy);
	}
	
        macroField.connectAll();
	setField(macroField);

    }

    final double standardBatchSize;

    
    /** This method is called by an external customer when it needs
	this supplier to send out some amount of stuff. It "loads some
	stuff on a ship", i.e. puts it into the delay.

	<p>This method makes a needProd.provide() call, which will
	"prime the system" by starting the first mkBatch(), if needed
	and possible. After that, the production cycle will repeat via
	the slackProvider mechanism.

    */
    void receiveOrder(double amt) {
	outstandingOrderAmount += amt;
	everOrdered += amt;
	
	needProd.provide(prodDelay);
	
    }

 
    /** Tries to make a batch, if not disabled, and if the plans allow that
	@return true if a batch was made; false if not enough input resources
	was there to make one, or the current plan does not call for one

    */
    public boolean mkBatch() {
	if (outstandingOrderAmount<=0) return false;

	double x = standardBatchSize;
	double t = state.schedule.getTime();

	
	Resource batch = (prototype instanceof Batch) ? ((Batch)prototype).mkNewLot(x, t) :
	    new CountableResource((CountableResource)prototype, x);

	Provider provider = null;
	
	double a = batch.getAmount();
	
	double y = (batch instanceof Batch)? ((Batch)batch).getContentAmount() : ((CountableResource)batch).getAmount();
	
	boolean z = needProd.accept( provider, batch, a, a);
	
	if (!z) throw new AssertionError("needProd is supposed to accept everything, but it didn't!");
	
	if (Demo.verbose)	    System.out.println( "Now needProd has="+needProd.hasBatches());
	
	outstandingOrderAmount -= y;
	
	startedProdBatches++;
	return true;
    }
    
    public String report() {

	String ba = (prototype instanceof Batch) ? " ba" : "";
	
	String s =// "[" + cname()
	    //+ "."+getName()+ "("+getTypical().getName()+")" +
	    "Ever ordered="+everOrdered+
	    "; ever started production="+	startedProdBatches+ " ba" +
	    ". Of this, "+
	    " still in factory=" + needProd.hasBatches() + ba +
	    ", in transit " +  needTrans.hasBatches() + ba +
	    (stolenGoodsSink.getEverConsumed()>0? ", stolen " + (long)stolenGoodsSink.getEverConsumedBatches()  + " ba":"") +
	    ", in QA " +  Util.ifmt(needQa.getAvailable()) +  "+" +  Util.ifmt(qaDelay.getDelayed());
	s += ba + ". ";
	s += "QA discarded=" + qaDelay.badResource + " ("+qaDelay.badBatches+ " ba)" +
	    ", QA released=" + qaDelay.releasedGoodResource + " ("+qaDelay.releasedBatches+" ba)";

	long missing = startedProdBatches -
	    (long)(needProd.getAvailable() +
		   prodDelay.getDelayed() +
		   (needTrans!=null? needTrans.getAvailable():0) +
		   transDelay.getDelayed() +
		   stolenGoodsSink.getEverConsumedBatches() +
		   needQa.getAvailable() +
		   qaDelay.getDelayed() +
		   qaDelay.badBatches+ qaDelay.releasedBatches);
	if ( prototype instanceof Batch &&     missing!=0) s += ". Missing " + missing + " ba";

	if (sm.outputSplitter !=null) 	s += "\n" + sm.outputSplitter.report();
	
	return wrap(s);
    } 

    /** Good resource released by QA today. Used in charting */
    private double releasedAsOfYesterday=0;
    
    /** Does nothing other than logging. */
    public void step(SimState state) throws IllegalArgumentException {
	Vector<Disruption> vd = ((Demo)state).hasDisruptionToday(Disruptions.Type.Delay, getName());
	if (vd.size()==1) {
	    // activate modified delay distribution
	    try {
		needTrans.setDelayDistribution( para.getDistribution("transDelay",state.random, vd.get(0).magnitude));
	    } catch( IllegalInputException ex) {
		throw new IllegalArgumentException(ex.getMessage());
	    }
	} else if (vd.size()>1) {
	    throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	} else {
	    // resume normal delay
	    needTrans.setDelayDistribution(transDelayDistribution);
	}

	vd = ((Demo)state).hasDisruptionToday(Disruptions.Type.ShipmentLoss, getName());
	if (vd.size()==1) {
	    double t = state.schedule.getTime();
	    //System.out.println(getName() + ": shipments will be stolen from " + t + " to " + (t+vd.get(0).magnitude));
	    
	    // set up shipment loss, in effect for a specified number of days
	    stolenGoodsSink.onUntil.enableUntil(t+vd.get(0).magnitude);
	} else if (vd.size()>1) {
	    throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	}

	vd = ((Demo)state).hasDisruptionToday(Disruptions.Type.Adulteration, getName());
	if (vd.size()==1) {
	    double t = state.schedule.getTime();

	    
	    if (prototype instanceof Batch) {
		// reduce quality of newly produced lots, in effect for 1 day
		prodDelay.setFaultRateIncrease(0.1 * vd.get(0).magnitude, t+1);
	    } else {
		// activate the increase at the QA stage instead
		qaDelay.setFaultRateIncrease(0.1 * vd.get(0).magnitude, t+1);
	    }

	    
	} else if (vd.size()>1) {
	    throw new IllegalArgumentException("Multiple disruptions of the same type in one day -- not supported. Data: "+ Util.joinNonBlank("; ", vd));
	}

	double releasedAsOfToday = qaDelay.getReleasedGoodResource();
	double releasedToday = releasedAsOfToday - releasedAsOfYesterday;
	releasedAsOfYesterday = releasedAsOfToday;
	charter.print(releasedToday);
    }


}
