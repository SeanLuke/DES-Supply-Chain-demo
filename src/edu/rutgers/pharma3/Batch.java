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

    static long lotNoGen = 0;

    static long nextLotNo() {
	return ++lotNoGen;
	
    }

    /** How soon after being created will this product expire. This is 
	measured in the same units as used in the simulation Scheduler,
	i.e. days. The value of Double.POSITIVE_INFINITY means "never expires"
    */
    double shelfLife;



    public String toString() {
	String s = "Batch(" + getName() + ", storage=" + getStorage();
	if (getStorage()!=null) {
	    for(Resource r: getStorage()) s += ", " + r;
	}
	s += ")";
	return s;

    }
    
    long lotNo;
    /** Creates "typicals" (prototype batches), rather than actual batches */
    Batch(CountableResource typicalUnderlying, double _shelfLife) {
	super(  "Batch of " + typicalUnderlying.getName());
	shelfLife = _shelfLife;
	setStorage( new Resource[] {typicalUnderlying});
	//System.out.println("Created: " +this);
    }

    /**
       @param para A ParaSet that has a parameter named "expiration", to get
       the shelf life from.
     */
    static Batch mkPrototype(CountableResource typicalUnderlying, ParaSet para)
	throws IllegalInputException     {
	return new Batch( typicalUnderlying, para.getDouble("expiration"));
    }
    
    /** Creates a new batch of underlying resource, with
	a specified lot number.
	@param prototype A "prototype" batch, from which we
	copy the name and the type of underlying resource
	@param _lotNo
	@param amount The amount of underlying resource
    */
    private Batch(Batch prototype, long _lotNo, double amount) {
	super(prototype);
	shelfLife = prototype.shelfLife;
	lotNo = _lotNo;
	CountableResource r0 = prototype.getContent();
	CountableResource r = new CountableResource(r0, amount);
	setStorage( new Resource[] {r});	    
	//System.out.println("Created2: " + this);
    }

    /** Creates another batch with the same lot number and
	a copy of the stored resource */
    public Resource duplicate() {
	Batch b = new Batch(this, lotNo, getContentAmount());
	//System.out.println("Duplicated: " + b);
	return b;
    }

    /** Creates a Batch of the same type as this Batch, with a new
	lot number and a specified amount of resource stored in it.
	@param now The "birthdate" (manufacturing date) of this lot
    */
    public Batch mkNewLot(double size, double now) {
	Batch b = new Batch(this, nextLotNo(),  size);
	Lot.registerLot(b.lotNo, now, now + shelfLife);
	return	b;
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
    

    
		
}
    
