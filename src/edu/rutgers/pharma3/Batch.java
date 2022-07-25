package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** A Batch object stores a specified amount of the underlying
    resource (e.g. drug), and has a manufacturer lot number
    associated with it.  The lot number can be used to look up
    some information about the lot (manufacturing date, factory
    name, expiration date, maybe some of the "life history"
    of the lot). 
    
    A batch object can represent e.g. a pallet on which several
    boxes of a drug, all with the same lot number, are stored.  It
    is possible for multiple batches to refer to the same lot
    number, if a single lot has been split into several batches.
*/
class Batch extends Entity {

    /** Used to generate unique sequential lot numbers */
    private static long lotNoGen = 0;

    /** Generates a unique lot number, which then can be assigned to a new lot */
    static long nextLotNo() {
	return ++lotNoGen;
	
    }

    /** If true, the expiration date is set based on the earliest expiration
	date of the inputs, rather than as the manufacturingDate + shelfLife.
	This is suitable e.g. for production stages that simply repackage
	an already-made product.
    */
    private boolean inheritsExpiration = false;

    public boolean getInheritsExpiration() {
	return inheritsExpiration;
    }

    
    /** How soon after being created will the product in this lot
	expire? This is measured in the same units as used in the
	simulation Scheduler, i.e. days. The value of
	Double.POSITIVE_INFINITY means "never expires". If
	inheritsExpiration==true, this variable is ignored.
    */
    private double shelfLife;

    public String toString() {
	String s =  getName(); // + ", storage=" + getStorage();
	if (getStorage()!=null) {
	    for(Resource r: getStorage()) {
		s +=  " (" + r.getType() + ") " + (long)r.getAmount();	      
	    }
	}
	return s;

    }
    
    long lotNo;
    /** Creates "typicals" (prototype batches), rather than actual batches */
    private Batch(CountableResource typicalUnderlying, boolean _inheritsExpiration, Double _shelfLife) {
	super(  "Batch of " + typicalUnderlying.getName());
	inheritsExpiration = _inheritsExpiration;
	shelfLife = (_shelfLife==null)? Double.POSITIVE_INFINITY :_shelfLife;
	setStorage( new Resource[] {typicalUnderlying});
	//System.out.println("Created: " +this);
    }

    /** Creates a "typical", i.e. a prototype batch object for a
	particular product. Such object does not represent a specific
	"real" batch, but is used as a pattern based on which real
	batches of the same product will be created.

	@param typicalUnderlying A CountableResource describing the
	product (e.g. a particular chemical) which is "packaged" in
	batches of this kind. The name of this resource is used to retrieve
	the parameters for the resource.

	@param config The config file, in which we expect to find a parameter set (ParaSet) with a name for the drug; that ParaSet may have a parameter named "expiration", to get she shelf life from.
   
	@param para A ParaSet that has a parameter named "expiration", to get
	the shelf life from.
     */
    static Batch mkPrototype(CountableResource typicalUnderlying,
			     Config config  //,     ParaSet para
			     )
	throws IllegalInputException     {
	String uname = typicalUnderlying.getName();
	ParaSet para = config.get(uname);
	if (para==null) throw new  IllegalInputException("No config parameters specified for product named '" + uname +"'");

	return new Batch(typicalUnderlying,
			 para.getBoolean("inheritsExpiration", false),
			 para.getDouble("expiration",Double.POSITIVE_INFINITY));
    }
    
    /** Creates a new batch of underlying resource, with
	a specified lot number.
	@param prototype A "prototype" batch, from which we
	copy the name and type of underlying resource, as well
	as its shelf life.
	@param _lotNo
	@param amount The amount of underlying resource
    */
    private Batch(Batch prototype, long _lotNo, double amount) {
	super(prototype);
	inheritsExpiration = prototype.inheritsExpiration;
	shelfLife = prototype.shelfLife;
	lotNo = _lotNo;
	CountableResource r0 = prototype.getContent();
	CountableResource r = new CountableResource(r0, amount);
	setStorage( new Resource[] {r});	    
	//System.out.println("Created2: " + this);
    }

    /** Creates another batch with the same lot number and
	a copy of the stored resource. Not sure why we'd need
	this method, but let's have since all Resource classes
	seem to do so.
    */
    public Resource duplicate() {
	Batch b = new Batch(this, lotNo, getContentAmount());
	//System.out.println("Duplicated: " + b);
	return b;
    }

    /** Creates a Batch of the same type as this Batch, with a new
	lot number and a specified amount of resource stored in it.
	@param now The "birthdate" (manufacturing date) of this lot
	@param inputs If not null, contains the list of Batch inputs
	that were used to make this lot. (Fungible, i.e. CountableResource,
	inputs, are ignored and not included into this list). If this
	product is of the "inheritExpiration" date, this list of inputs
	is used to set the expiration date of the new batch;
    */
    public Batch mkNewLot(double size, double now, Vector<Batch> inputs) {
	Batch b = new Batch(this, nextLotNo(),  size);
	double exp;

	if (inheritsExpiration) {
	    if (inputs==null) throw new IllegalArgumentException("To make a lot of " + getName() +", we need to know the inputs' expiration dates");
	    exp =  earliestExpirationDate(inputs);
	} else {
	    exp = now + shelfLife;
	}

	Lot.registerLot(b.lotNo, now, exp);
	return	b;
    }

    public Batch mkNewLot(double size, double now) {
	return  mkNewLot( size, now, null);
    }

    /** What is the earliest expiration date among all the batches in the list? */
    private static double earliestExpirationDate(Vector<Batch> batches) {
	double d = Double.POSITIVE_INFINITY;
	for(Batch b: batches) {
	    d = Math.min(d, b.getLot().expirationDate);
	}
	return d;
    }


    /** Accesses the underlying resource (drug etc) "packaged" in this batch */
    CountableResource getContent() {
	return (CountableResource)getStorage()[0];
    }

    /** How much drug etc this batch contains */
    double getContentAmount() {
	if (getStorage()==null) throw new IllegalArgumentException("Bad batch: storage==null!");
	return getContent().getAmount();
    }

    /** An alternative to Provider.getAvailable(), which
	looks at the amount of content inside Batches.
     */
    static double getAvailableContent(Provider p) {
	Entity[] ee = p.getEntities();
	if (ee==null) return p.getAvailable();
	else {
	    double sum = 0;
	    for(Entity e: ee) {
		if (!(e instanceof Batch)) throw new IllegalArgumentException("Expected a batch, found " + e);
		sum += ((Batch)e).getContentAmount();
	    }
	    return sum;
	}
  
    }
    
    Lot getLot() {
	return Lot.get(lotNo);
    }

    boolean hasExpired(double now) {
	return Lot.get(lotNo).hasExpired(now);
    }

    /** Will this lot expire within a specified number of days from now? */
    boolean willExpireSoon(double now, double within) {
	return  hasExpired(now+within);
    }


		
}
    
