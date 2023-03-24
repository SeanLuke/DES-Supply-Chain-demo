package  edu.rutgers.supply;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.IllegalInputException;
import edu.rutgers.util.Config;
import edu.rutgers.util.ParaSet;

/** A Batch object stores a specified amount of the underlying
    resource (e.g. drug), and has a manufacturer lot number
    associated with it.  The lot number can be used to look up
    some information about the lot (manufacturing date, factory
    name, expiration date, maybe some of the "life history"
    of the lot). 
    
    A Batch object can represent "prototype batch" (a model
    used for creating real product lots) or a "real batch"
    (representing an actual lot of the product.

    A "real batch" is e.g. a pallet on which several boxes of
    a drug, all with the same lot number, are stored.  It is possible
    for multiple batches to refer to the same lot number, if a single
    lot has been split into several batches.

    A prototype batch stores a Batch.PrototypeInfo object in its
    Entity.info field, while a real batch stores a LotInfo object in
    that field. The former contains information (rules) that is used
    in generating the latter.

*/
public class Batch extends Entity {

    /** A PrototypeInfo instance, stored in each prototype lot (but
	not in actual lots) describes some properties of a type of
	product (e.g. "aspirin in bulk", "aspirin in bottles", or
	"trail mix"). The data stored here are used to help properly
	set parameters during the construction of actual lots of this
	product.

	<p>
	As far as the expiration date is concerned, there are two types
	of products, distinguished by the flag <tt>inheritsExpiration</tt>:
	<ol>
	
	<li>Own expiration (inheritsExpiration = false). For these
	products, the expiration date is computed as the manufacturing
	date plus the standard shelf life (say, 24 months). This is the
	case with most products.

	<li>Inherited expiration date (inheritsExpiration = true). For
	these products, the expiration date is set based
	on the earliest expiration date of the inputs, rather than as
	the manufacturingDate + shelfLife.  This is suitable e.g. for
	production stages that simply repackage an already-made
	product. (E.g. when you make trail mix, the expiration data of
	the package of the product should be set to the earliest of
	the expiration dates of the lots of nuts, raisins, and
	crackers that went into the mix).
	
	</ol>
     */
    static class PrototypeInfo {

	/** If true, this is an "inherits expiration" product, whose
	expiration date of this product is set based on the earliest
	expiration date of the inputs, rather than as the
	manufacturingDate + shelfLife.
	*/
	private boolean inheritsExpiration = false;

	//public boolean getInheritsExpiration() {  return inheritsExpiration;  }

	
	/** How soon after being created will the product in this lot
	    expire? This is measured in the same units as used in the
	    simulation Scheduler, i.e. days. The value of
	    Double.POSITIVE_INFINITY means "never expires". If
	    inheritsExpiration==true, this variable is ignored.
	*/
	private double shelfLife;

	/** This field may be set to non-null only in prototype
	    batches with inheritsExpiration==true.  It is used to
	    initialize the expiration date of batches that appear "ex
	    nihilo" (e.g. for the initial stock at the begining of the
	    simularion) rather than are produced during the simulation
	    intself from identifiable input batches, and whose
	    expiration date therefore cannot be "inherited" from the
	    inputs.
	 */
	private Double backupShelfLife;

	/** Creates the batch prototype structure for a new product. */
	/*
	PrototypeInfo(boolean _inheritsExpiration, Double _shelfLife, Double _backupShelfLife) {
	    inheritsExpiration = _inheritsExpiration;
	    shelfLife = (_shelfLife==null)? Double.POSITIVE_INFINITY :_shelfLife;
	    if (inheritsExpiration) backupShelfLife =  _backupShelfLife;
	    
	    //System.out.println("Created PI = " +this);
 	    
	}
	*/

	PrototypeInfo(ParaSet para) throws IllegalInputException {	
	    inheritsExpiration =  para.getBoolean("inheritsExpiration", false);
	    shelfLife = para.getDouble("expiration",Double.POSITIVE_INFINITY);
	    backupShelfLife =  inheritsExpiration?   
		para.getDouble("backupExpiration", null):
		null;
	}

	
	/** Creates the lot information structure for a new product lot
	    (a "real batch") based on this prototype batch.
	    @param name The name of the product, e.g. "Aspirin". This
	    is just used in error messages.
	    @param now The current simulation time, to properly initialize
	    the expiration date.
	    @param input The batches of ingredients. This is only used if this
	    is an inherits-expiration product, to properly set its expiration
	    date.
	    
	*/
	LotInfo newLot(String name, double now, Vector<Batch> inputs) {
	    double exp;

	    if (inheritsExpiration) {
		if (inputs==null) {
		    if (backupShelfLife !=null) exp = now + backupShelfLife;
		    else throw new IllegalArgumentException("To make a lot of " + name +", we need to know the inputs' expiration dates");
		} else {
		    exp =  earliestExpirationDate(inputs);
		}
	    } else {
		exp = now + shelfLife;
	    }

	    double earliestOrigin =
		(inputs==null)? now:
		Math.min(now, earliestAncestorManufacturingDate(inputs));
   	  	    
	    LotInfo li = LotInfo.newLot(now, exp, earliestOrigin);
	    return li;
	}

	public String toString() {
	    return "(Prototype lot, " +
		(inheritsExpiration?" inherits exp": "shelf life=" + shelfLife) +
		")";
	}
 	
    }
  	
    

    public String toString() {
	String s =  getName(); // + ", storage=" + getStorage();
	s += ", info="+getInfo();

	if (getStorage()!=null) {
	    for(Resource r: getStorage()) {
		s +=  " (" + r.getType() + ") " + (long)r.getAmount();	      
	    }
	}
	return s;
    }

    /** This is used for e.g. looking up resource-related properties 
	in config files. This allows one to have config lines starting with
<pre>
Foo,
</pre>
for both a countable resource named "Foo" and for a Batch of "Foo".
     */
    public static String getUnderlyingName(Resource r) {
	//System.out.println("DEBUG: gUN(r=" + r+")");

	return (r instanceof Batch)?  ((Batch)r).getUnderlyingName() : r.getName();
    }

    /** Retrieves the name of the underlying resource */
    public String getUnderlyingName() {
	return getUnderlying().getName();
    }

    /** @return The underlying resource */
    public CountableResource getUnderlying() {
	return (CountableResource)(getStorage()[0]);
    }
    
    /** Creates a "typical" (prototype) batch, with a PrototypeInfo
        structure in it, rather than an actual batch. This constructor
	is only used by mkPrototype().
    */
    private Batch(CountableResource typicalUnderlying,
		  PrototypeInfo pi) {
	super(  "BatchOf" + typicalUnderlying.getName());
	setInfo( pi);
	setStorage( new Resource[] {typicalUnderlying});
	//System.out.println("Created Prototype Batch = " +this);
    }

    /** Creates a "typical", i.e. a prototype batch object for a
	particular product. Such object does not represent a specific
	"real" batch, but is used as a pattern based on which real
	batches of the same product will be created.

	<p>The name of the batch product will be based on the name of the
	underlying.

	@param typicalUnderlying A CountableResource describing the
	product (e.g. a particular chemical) which is "packaged" in
	batches of this kind. The name of this resource is used to retrieve
	the parameters for the resource.

	@param config The config file, in which we expect to find a parameter set (ParaSet) with a name for the drug; that ParaSet may have a parameter named "expiration", to get she shelf life from.
   
	@param config A configuration file which has a section (a
	ParaSet) with the name that's the same as that of the
	typicalUnderlying; that ParaSet has a parameter named
	"expiration", to get the shelf life from.
     */
    public static Batch mkPrototype(CountableResource typicalUnderlying,
			     Config config 	     )
	throws IllegalInputException     {
	String uname = typicalUnderlying.getName();
	ParaSet para = config.get(uname);
	if (para==null) throw new  IllegalInputException("No config parameters specified for product named '" + uname +"'");

	PrototypeInfo pi = new PrototypeInfo(para);

	Batch b = new Batch(typicalUnderlying, pi);

	return b;

    }
    
    /** Creates a new batch of underlying resource, with
	a specified resource amount and a unique lot number.
	@param prototype A "prototype" batch, from which we
	copy the name and type of underlying resource, as well
	as its shelf life.
	@param lot a newly created structure that contains the unique lot number, expiration date etc for the new lot
	@param amount The amount of underlying resource
    */
    protected Batch(Batch prototype, LotInfo lot, double amount) {
	super(prototype); // this sets name and type
	setInfo(lot);
	CountableResource r0 = prototype.getContent();
	CountableResource r = new CountableResource(r0, amount);
	setStorage( new Resource[] {r});	    
	//System.out.println("Created2: " + this);
    }

    /** Duplicates the prototype. Just used for duplicate() */    
    private Batch(Batch prototype) {
	super(prototype); // this sets name and type
    }
    
    
    /** Creates another batch with the same lot number and
	a copy of the stored resource. Not sure why we'd need
	this method, but let's have it, since all Resource classes
	seem to do so.
    */
    public Resource duplicate() {
	if (getInfo() instanceof LotInfo) {
	    Batch b = new Batch(this, (LotInfo)getInfo(), getContentAmount());
	    //System.out.println("Duplicated: " + b);
	    return b;
	} else { // prototype
	    return new Batch(this);
	}
    }
    
    /** Creates a "real" Batch of the same type as this prototype
	Batch, with a new unique lot number, appropriate expiration
	date, and a specified amount of resource stored in it.
	This method should only be called on prototype batches.
	
	@param now The "birthdate" (manufacturing date) of this lot
	@param inputs If not null, contains the list of Batch inputs
	that were used to make this lot. (Fungible,
	i.e. CountableResource, inputs, should be ignored by the
	caller, and not included into this list). If this product is
	of the "inheritExpiration" date, this list of inputs is used
	to set the expiration date of the new batch;
    */
    public Batch mkNewLot(double size, double now, Vector<Batch> inputs) {
	Object info = getInfo(); 
	if (info==null || !(info instanceof PrototypeInfo)) throw new IllegalArgumentException("Only can do mkNewLot on a prototype lot, and this one isn't. info=" + info +"; this=" + this);
	
	LotInfo lot =  ((PrototypeInfo)info).newLot(getName(), now, inputs);
	Batch b = new Batch(this, lot,  size);
	return	b;
    }

    /** A convenience method, for resources that don't have inputs
	that we need to pay attention to */
    public Batch mkNewLot(double size, double now) {
	return  mkNewLot( size, now, null);
    }

    /** Reduce this lot by the specified amount, and move the
	removed amount to a new lot.
	@param amount the amount to remove from this lot and put into the new lot
	@return  the new lot 
    */
    public Batch split(double amount) {
	// ZZZZ
	if (amount>getContentAmount()) throw new IllegalArgumentException("Want to split off more than there is in this batch");
	else 	if (amount==getContentAmount()) throw new IllegalArgumentException("Want to split off the entire content of this batch");

	getContent().decrease(amount); 
	return new Batch(this, getLot().split(), amount);
    }

    /** Adds the resource from the other batch to this one */
    public void merge(Batch other) {
	getContent().add(other.getContent());
	getLot().effectMerge(other.getLot());
    }
    
    
    /** What is the earliest expiration date among all the batches in the list? 
	@return the earliest expiration date, or  Double.POSITIVE_INFINITY if
	the array of inputs is empty
     */
    private static double earliestExpirationDate(Vector<Batch> batches) {
	double d = Double.POSITIVE_INFINITY;
	for(Batch b: batches) {
	    d = Math.min(d, b.getLot().expirationDate);
	}
	return d;
    }

    /** Scans all input lots for their earliestAncestorManufacturingDate field.
	This is useful for "lifetime tracing" tools, not for the SC simulation
	itself.
     */
    private static double earliestAncestorManufacturingDate(Vector<Batch> batches) {
	double d = Double.POSITIVE_INFINITY;
	for(Batch b: batches) {
	    d = Math.min(d, b.getLot().earliestAncestorManufacturingDate);
	}
	return d;
    }
  

    /** Accesses the underlying resource (drug etc) "packaged" in this batch */
    public CountableResource getContent() {
	return (CountableResource)getStorage()[0];
    }

    /** How much prioduct (e.g. pills of a drug, etc) this batch contains */
    public double getContentAmount() {
	if (getStorage()==null) throw new IllegalArgumentException("Bad batch: storage==null!");
	return getContent().getAmount();
    }

    /** How much of underlying resource (e.g. how any pills) does a
	given Resource obeject represent? This method should provide a
	correct answer regardless of whether r is represented as some
	amount of fungible CountableResource, or as a packaged Batch.
	
	@param r Either a Batch or a CountableResource.
     */
    public static double getContentAmount(Resource r) {
	return (r instanceof Batch)?  ((Batch)r).getContentAmount() : r.getAmount();
    }

   
    /** How much "stuff" (e.g. how many pills) does the Provider have
	on hand? This is an alternative to Provider.getAvailable(),
	which, if appropriate, looks at the amount of content inside
	Batches.  This, of course, is inefficient, so if possible one
	needs to use running-total accounting, such as
	Pool.currentStock on InputStore.currentStock.
	@param p Provider of any kind (either of CountableResource, or of
	Batch Entities)
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

    /** If this is a "real" batch, retrieves its LotInfo structure (which
	contains the lot number, expiration date, etc).
    */
    public LotInfo getLot() {
	Object info = getInfo();
	if (info==null || !(info instanceof LotInfo)) throw new IllegalArgumentException("Cannot do getLot on what appears to be a prototype lot");
	return (LotInfo)info;
    }

    /** Has this lot expired, as of the specified date? */
    public boolean hasExpired(double now) {
	return getLot().hasExpired(now);
    }

    /** Will this lot expire within a specified number of days from now? */
    public boolean willExpireSoon(double now, double within) {
	return  hasExpired(now+within);
    }

    /** Adds text to the info message inside the batch's LotInfo object.
	You can think of this as of warehouse workers, truck drivers, etc
	writing notes on a slip of paper attached to the pallet.
     */
    public void addToMsg(String s) {
	LotInfo li = getLot();
	if (li!=null) li.addToMsg(s);
    }
	
    
}
    
