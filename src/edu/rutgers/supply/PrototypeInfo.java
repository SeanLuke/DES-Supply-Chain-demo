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

/** An auxiliary class for Batch.

    <p> A PrototypeInfo instance, stored in each prototype lot (but
    not in actual lots) describes some properties of a type of product
    (e.g. "aspirin in bulk", "aspirin in bottles", or "trail
    mix"). The data stored here are used to help properly set
    parameters during the construction of actual lots of this product.
	
    <p>
    As far as the expiration date is concerned, there are two types
    of products, distinguished by the flag <tt>inheritsExpiration</tt>:
    <ol>
	
    <li>Own expiration (inheritsExpiration = false). For these
    products, the expiration date is computed as the manufacturing
    date plus the standard shelf life (say, 24 months). This is the
    case with most products.
    
    <li>Inherited expiration date (inheritsExpiration = true). For
    these products, the expiration date is set based on the earliest
    expiration date of the inputs, rather than as the
    manufacturingDate + shelfLife.  This is suitable e.g. for
    production stages that simply repackage an already-made
    product. (E.g. when you make trail mix, the expiration data of the
    package of the product should be set to the earliest of the
    expiration dates of the lots of nuts, raisins, and crackers that
    went into the mix).
	
    </ol>
*/
class PrototypeInfo {

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

	public boolean equals(Object _o) {
	    if (!(_o instanceof PrototypeInfo)) return false;
	    PrototypeInfo o = (PrototypeInfo)_o;
	    return o.inheritsExpiration == inheritsExpiration &&
		o.shelfLife == shelfLife &&
		o.backupShelfLife == backupShelfLife;
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
  


    
}
  	
    

