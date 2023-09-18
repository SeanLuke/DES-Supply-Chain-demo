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
import edu.rutgers.util.Util;

/** Batch2 is like Batch, but can store resource not just of one
    type, but of any of the several specified types.

    <p>
   A Batch object stores a specified amount of the underlying
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
public class Batch2 extends Batch {

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
	if (getStorage().length!=1) throw new IllegalArgumentException("getUnderlying not supported on prototype batches...");
	
	return (CountableResource)(getStorage()[0]);
    }
    
    /** Creates a "typical" (prototype) batch, with a PrototypeInfo
        structure in it, rather than an actual batch. This constructor
	is only used by mkPrototype().

	@param  typicalUnderlying A list of things that this type of batches
	can store. (E.g. {Jeans, Shirt, Skirt})
    */
    private Batch2(CountableResource[] typicalUnderlying,
		  PrototypeInfo pi) {
	super(  "batch." + joinNames(typicalUnderlying));
	setInfo( pi);
	
	setStorage(typicalUnderlying);
		   //new Resource[] {typicalUnderlying});
	//System.out.println("Created Prototype Batch = " +this);
    }

    private static String joinNames(CountableResource[] typicalUnderlying) {
	String [] v = new String[typicalUnderlying.length];
	for(int j=0; j<v.length; j++) v[j] = typicalUnderlying[j].getName();
	return Util.joinNonBlank("_or_", v);
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

   
	@param config A configuration file which has a section (a
	ParaSet) with the name that's the same as that of the
	typicalUnderlying; that ParaSet has a parameter named
	"expiration", to get the shelf life from.
     */
    public static Batch2 mkPrototype(CountableResource typicalUnderlying[],
				     Config config 	     )
	throws IllegalInputException     {
	PrototypeInfo pi = null;
	for(int j=0; j<typicalUnderlying.length; j++) {
	    String uname = typicalUnderlying[j].getName();
		
	    ParaSet para = config.get(uname);
	    if (para==null) {
		if (pi!=null) continue;
		else throw new  IllegalInputException("No config parameters specified for product named '" + uname +"'");
	    }
	    
	    PrototypeInfo z = new PrototypeInfo(para);
	    if (pi==null) pi = z;
	    else if (!pi.equals(z)) throw new IllegalArgumentException("The parameters for resource " + uname + " are not the same as for " + typicalUnderlying[0].getName() + ". At present, there is no support for storing them in the same Batch2");
	}
								       

	Batch2 b = new Batch2(typicalUnderlying, pi);

	return b;

    }
    
    /** Creates a new batch of underlying resource, with
	a specified resource amount and a unique lot number.
	@param prototype A "prototype" batch, from which we
	copy the name and type of underlying resource, as well
	as its shelf life.
	@param r0 the "prototype" for the underlying resource to pick
	@param lot a newly created structure that contains the unique lot number, expiration date etc for the new lot
	@param amount The amount of underlying resource
    */
    protected Batch2(Batch2 prototype, CountableResource r0, LotInfo lot, double amount) {
	super(prototype); // this sets name and type
	setInfo(lot);
	//CountableResource r0 = prototype.getContent();


	if (!prototype.isMatchingResourceType(r0)) throw new IllegalArgumentException("Attempt to create a Batch2 object with a wrong underling type (" + r0 +"), not allowed by the prototype");
	Resource[] rr = {new CountableResource(r0, amount)};
	setStorage(rr);	    
	//System.out.println("Created2: " + this);
    }

    /** Duplicates the prototype. Just used for duplicate() */    
    private Batch2(Batch2 prototype) {
	super(prototype); // this sets name and type
    }
    
    
    /** Creates another batch with the same lot number and
	a copy of the stored resource. Not sure why we'd need
	this method, but let's have it, since all Resource classes
	seem to do so.
    */
    public Resource duplicate() {
	if (getInfo() instanceof LotInfo) {
	    Batch2 b = new Batch2(this, (CountableResource)getStorage()[0], (LotInfo)getInfo(), getContentAmount());
	    //System.out.println("Duplicated: " + b);
	    return b;
	} else { // prototype
	    return new Batch2(this);
	}
    }
    
    /** Creates a "real" Batch of the same type as this prototype
	Batch, with a new unique lot number, appropriate expiration
	date, and a specified amount of resource stored in it.
	This method should only be called on prototype batches.

	@param r Prototype resource
	
	@param now The "birthdate" (manufacturing date) of this lot
	@param inputs If not null, contains the list of Batch inputs
	that were used to make this lot. (Fungible,
	i.e. CountableResource, inputs, should be ignored by the
	caller, and not included into this list). If this product is
	of the "inheritExpiration" date, this list of inputs is used
	to set the expiration date of the new batch;
    */
    public Batch2 mkNewLot(CountableResource r, double size, double now, Vector<Batch> inputs) {
	Object info = getInfo(); 
	if (info==null || !(info instanceof PrototypeInfo)) throw new IllegalArgumentException("Only can do mkNewLot on a prototype lot, and this one isn't. info=" + info +"; this=" + this);
	
	LotInfo lot =  ((PrototypeInfo)info).newLot(getName(), now, inputs);
	Batch2 b = new Batch2(this, r, lot,  size);
	return	b;
    }

    /** A convenience method, for resources that don't have inputs
	that we need to pay attention to */
    public Batch2 mkNewLot(CountableResource r, double size, double now) {
	return  mkNewLot(r, size, now, null);
    }

    /** Reduce this lot by the specified amount, and move the
	removed amount to a new lot.
	@param amount the amount to remove from this lot and put into the new lot
	@return  the new lot 
    */
    public Batch2 split(double amount) {
	if (amount>getContentAmount()) throw new IllegalArgumentException("Want to split off more than there is in this batch");
	else 	if (amount==getContentAmount()) throw new IllegalArgumentException("Want to split off the entire content of this batch");

	getContent().decrease(amount); 
	return new Batch2(this, (CountableResource)getStorage()[0], getLot().split(), amount);
    }
    
  
    
}
    
