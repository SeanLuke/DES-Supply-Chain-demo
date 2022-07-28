package  edu.rutgers.pharma3;

import java.util.Vector;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

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
public class MaterialSupplier extends Macro
    implements  //Receiver,
			    Named,
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

    private final sim.des.Queue needProd, needTrans, needQa;

        
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

    /** Creates a Queue to be attached in front of the Delay, and
	links it up appropriately. This is done so that we can model a
	production facility (or a transportation service, etc) which can
	handle no more than a given number of batches at any given
	time.
	
	@param delay Models the production step whose capacity we want to restrict. (For example, a bread oven with space for exactly 1 batch of loaves, or a truck that has space for exactly 1 shipping container of stuff).
	
	@param cap The max number of batches that the production unit (the Delay object) can physically handle simultaneously. (Usually, 1).

	@return A Queue object into which one can put any number of "raw" batches, where they will sit and wait for the production facility to grab them whenever it's ready. 
     */
    private sim.des.Queue controlInput(Delay delay, double cap) {
	sim.des.Queue need = new sim.des.Queue(state, prototype);
	need.setOffersImmediately(true);
	delay.setCapacity(cap);

	need.addReceiver(delay);
	delay.setSlackProvider(need);
	
	return need;
    }

    /** The tool to write a CSV file with data that can later be charted by an external program */	
    private Charter charter;
 
    
    /** @param resource The product supplied by this supplier. Either a "prototype" Batch, or a CountableResource.
     */
    private MaterialSupplier(SimState _state, ParaSet para,
			     Resource resource	     ) 	throws IllegalInputException, IOException {
	//super(state, resource);
	state = _state;
	prototype = resource;

	setName(para.name);
	System.out.println("MS " + getName()+", proto=" +prototype);

	
	standardBatchSize = para.getDouble("batch");

	double cap = (prototype instanceof Batch) ? 1:    standardBatchSize;	

	prodDelay = new Delay(state, resource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",state.random));
	needProd = controlInput(prodDelay, cap);
	
	
	transDelay = new Delay(state, resource);
	transDelay.setDelayDistribution(para.getDistribution("transDelay",state.random));
	needTrans = controlInput(transDelay, cap);
	
	qaDelay = QaDelay.mkQaDelay( para, state, resource);
	needQa = controlInput(qaDelay, cap);
		
	//--- link them all
	prodDelay.addReceiver( needTrans );
	transDelay.addReceiver( needQa );
	//-- the output for qaDelay will be added by setQaReceiver

	if (this instanceof Macro)  addProvider(qaDelay, false);
 
	charter=new Charter(state.schedule, this);
 	
    }

    /** Lays out icons for the GUI display of a product flow chart.

	(SVG won't work; only PNG is OK).

	@param x0 X offset for the top left corner
	@param y0 Y offset for the top left corner
 */
    void depict(DES2D field, int x0, int y0) {

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
        macroField.connectAll();
	setField(macroField);

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
	startAllProduction();
    }

    /** Initiate the production process on as many batches as needed.
	FIXME: we always use the standard batch size, and don't use 
	the last short batch, in order to simplify Production.
    */
    private boolean startAllProduction() {
	int bcnt = 0;
	double x = standardBatchSize;
	double t = state.schedule.getTime();
	while (outstandingOrderAmount>0) {
	//double x = Math.min(outstandingOrderAmount ,  standardBatchSize);

	    Resource batch = (prototype instanceof Batch) ? ((Batch)prototype).mkNewLot(x, t) :
	    new CountableResource((CountableResource)prototype, x);

	    Provider provider = null;
	    if (Demo.verbose) System.out.println( "At t=" + t+", " + getName() + " putting batch of "+x+" into needProd, bcnt=" + bcnt +", outstandingOrderAmount=" + outstandingOrderAmount);

	    double a =batch.getAmount();

	    double y = (batch instanceof Batch)? ((Batch)batch).getContentAmount() : ((CountableResource)batch).getAmount();

	    boolean z = needProd.accept( provider, batch, a, a);

	    if (!z) throw new AssertionError("needProd is supposed to accept everything, but it didn't!");

	    outstandingOrderAmount -= y;

	    startedProdBatches++;
	    bcnt ++;
	}
	if (Demo.verbose) System.out.println( "At t=" + t+", " + getName() + " has put "+bcnt + " batches to needProd");
	needProd.step(state); // causes offerReceivers() to the prodDelay
	return (bcnt>0);
    }
    
    public String report() {

	String ba = (prototype instanceof Batch) ? " ba" : "";
	
	String s =// "[" + cname()
	    //+ "."+getName()+ "("+getTypical().getName()+")" +
	    "Ever ordered="+everOrdered+
	    "; ever started production="+	startedProdBatches+ " ba" +
	    ". Of this, "+
	    " still in factory=" + Util.ifmt(needProd.getAvailable()) + "+" + Util.ifmt(prodDelay.getDelayed()) + ba +
	    ", on truck " +  Util.ifmt(needTrans.getAvailable()) + "+" + Util.ifmt(transDelay.getDelayed()) + ba + 
	    ", in QA " +  Util.ifmt(needQa.getAvailable()) +  "+" +  Util.ifmt(qaDelay.getDelayed());
	if (transDelay.getAvailable()>0) s += "+" +  (long)transDelay.getAvailable();
	s += ba + ". ";
	s += "QA discarded=" + qaDelay.badResource + " ("+qaDelay.badBatches+ " ba)" +
	    ", QA released=" + qaDelay.releasedGoodResource + " ("+qaDelay.releasedBatches+" ba)";

	long missing = startedProdBatches -
	    (long)(needProd.getAvailable() +
		   prodDelay.getDelayed() +
		   needTrans.getAvailable() +
		   transDelay.getDelayed() +
		   needQa.getAvailable() +
		   qaDelay.getDelayed() +
		   qaDelay.badBatches+ qaDelay.releasedBatches);
	if ( prototype instanceof Batch &&     missing!=0) s += ". Missing " + missing + " ba";

	
	return wrap(s);
    } 


    //String name;
    //    public String getName() { return name; }
    //public void setName(String name) { this.name = name; }
  
    /** For the "Named" interface. Maybe it should set the counters to 0... */
    //public void reset(SimState state) {}

    /** Good resource released by QA today. Used in charting */
    private double releasedAsOfYesterday=0;
    
    /** Does nothing other than logging. */
    public void step(SimState state) {
	double releasedAsOfToday = qaDelay.getReleasedGoodResource();
	double releasedToday = releasedAsOfToday - releasedAsOfYesterday;
	releasedAsOfYesterday = releasedAsOfToday;
	charter.print(releasedToday);
    }

  


}
