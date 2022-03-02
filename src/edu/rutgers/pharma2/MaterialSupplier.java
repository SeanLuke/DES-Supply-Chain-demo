package  edu.rutgers.pharma2;

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

    <p>The Receiever (Sink) feature of this class refers to the QA step.

    <p>A MaterialSupplier does not need to be scheduled. Rather, a controlling
    element (company headquearters) needs to do 2 things:
<ol>

<li>At the beginning, call setQaReceiver(Receiver rcv), to specify the
object to which this supplier should push QA'ed material.

<li>Call receiveOrder(double amt) whenever it wants to it orders some
 amount of the material. Internally, this will push some stuff into a
 delay, which will later trigger rcv.accept() on the downstream
 receiever. </ol>

 */
public class MaterialSupplier extends Sink //Delay
    implements // Receiver,
			       Reporting
{

    /** It is expected that it only returns numbers within [0:1] range */
    private final AbstractDistribution faultyPortionDistribution;

    private double everOrdered=0;
    private double badResource = 0, releasedGoodResource=0;

    public double getEverOrdered() { return everOrdered; }
    public double getBadResource() { return badResource; }
    public double getReleasedGoodResource() { return releasedGoodResource; }

 
    /** Transportation delay */
    private final Delay delay;

    //    public Resource	getTypical() {
    //    	return delay.getTypical();
    //    }
    
    /**
     */
    MaterialSupplier(SimState state, String name, Config config, CountableResource resource) 
	throws IllegalInputException {
	super(state, resource);

	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	//setCapacity(para.getDouble("capacity"));

	delay = new Delay(state, resource);
	delay.setDelayDistribution(para.getDistribution("delay",state.random));
	delay.addReceiver( this );

	faultyPortionDistribution = para.getDistribution("faulty",state.random);
	
    }

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
	everOrdered += amt;
	Provider provider = null;	
	CountableResource amount = new CountableResource( (CountableResource)getTypical(), amt);
	delay.accept( provider, amount, amt, amt);
    }


    /** This is invoked when a ship comes in (stuff comes out of the 
	transportation delay), and the product goes thru QA.
	Removes some portion of the material as faulty, and send
	the rest to the designated receiver. 
	@param provider The Delay object representing the transportation delay
    */ 
    public boolean accept​(Provider provider, Resource resource, double atLeast, double atMost) {

	CountableResource cr = (CountableResource) resource;
	double amt = cr.getAmount();
	if (amt > atMost) amt=atMost;

	double r = faultyPortionDistribution.nextDouble();
	if (r<0) r=0;
	if (r>1) r=1;

	double faulty = Math.round( amt * r);
	double good = amt - faulty;
	badResource += faulty;
	releasedGoodResource += good;
	cr.decrease(faulty);
	double atLeast1 = Math.max(0,  atLeast-faulty);
	double atMost1 = Math.max(0,  atMost-faulty);
	if (rcv==null) {
	    cr.decrease(good);
	    System.out.println("Warning: " +  cname() + "."+getName() + " has no receiver set!");
	    return true;
	} else {
	    return rcv.accept(provider, resource, atLeast1, atMost1);
	}
    }

    public String report() {
	String s =// "[" + cname()
	    //+ "."+getName()+ "("+getTypical().getName()+")" +
	    "Ever ordered="+everOrdered+
	    "; of this, still on ships=" +  delay.getTotal();
	if (delay.getAvailable()>0) s += "+" +  delay.getAvailable();
	s += ", QA discarded=" + badResource +
	    ", QA released=" + releasedGoodResource; //+	    "]";
 
	return wrap(s);
    } 


}
