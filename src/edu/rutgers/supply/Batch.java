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
	if (getStorage().length!=1) throw new AssertionError();
	return (CountableResource)(getStorage()[0]);
    }

    /** This must be only used from Batch2() */
    protected Batch(String name) {
	super(name);
	if (!(this instanceof Batch2)) throw new IllegalArgumentException("Wrong use of constructor Batch(String). It is only meant to be used to construct Batch2 objects");
    }
    
    /** Creates a "typical" (prototype) batch, with a PrototypeInfo
        structure in it, rather than an actual batch. This constructor
	is only used by mkPrototype().
    */
    private Batch(CountableResource typicalUnderlying,
		  PrototypeInfo pi) {
	super(  "batch." + typicalUnderlying.getName());
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

    /** Duplicates the prototype. Just used for duplicate(), and for a Batch2 constructor */    
    protected Batch(Batch prototype) {
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
	if (amount>getContentAmount()) throw new IllegalArgumentException("Want to split off more than there is in this batch");
	else 	if (amount==getContentAmount()) throw new IllegalArgumentException("Want to split off the entire content of this batch");

	getContent().decrease(amount); 
	return new Batch(this, getLot().split(), amount);
    }

    /** Adds the resource from the other batch to this one */
    public void merge(Batch other) {
	if (!getContent().isSameType(other.getContent())) throw new IllegalArgumentException("Cannot merge two batches with different content types: {" + this + "} and {" + other + "}");
	
	getContent().add(other.getContent());
	getLot().effectMerge(other.getLot());
    }
    
    
    /** Accesses the underlying resource (drug etc) "packaged" in this batch */
    public CountableResource getContent() {
	if (getStorage().length!=1) throw new AssertionError("This method should not be used on Batch2 prototypes");
	return (CountableResource)getStorage()[0];
    }

    /** How much product (e.g. pills of a drug, etc) this batch contains */
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

    /** Is the specified resource of the same as the resource in this Batch?
	(Or, if this Batch is the prototype object for a Batch2, the same as
	one of the allowed resources in the Batch2).
    */
    boolean isMatchingResourceType(CountableResource r) {
	for(Resource q: getStorage()) {
	    if (q.isSameType(r)) return true;
	}
	return false;
    }

    /** If this is a prototype batch, return a list of
	the prototype resource objects that this type of batches
	can represent. For the original Batch, the array always has 1 element;
	for Batch2, it can have several. */
    public CountableResource[] listUnderlying() {
	if (!(getInfo() instanceof PrototypeInfo)) throw new AssertionError();
	return (CountableResource[])getStorage();
    }
	    

    
    
}
    
