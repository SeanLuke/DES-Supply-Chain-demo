package  edu.rutgers.pharma3;

import java.util.*;
//import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

import edu.rutgers.util.*;

/** A Lot object contains useful information about a lot of product
    (which itself is modeled by a Batch object), such as the
    manufacturing date and expiration date. In the future, we may add
    various other fields to this class, e.g. some information about
    the provenance of a given lot, which may affect, determinstically or
    probabilistically, the future fate of the lot (e.g. the probability
    of it going bad later on, or the possibility of the discovery of
    contamination that wasn't caught by the normal QA process).
*/

public class Lot {
    /** This table stores information of all lots that have ever been created.
	It Maps lotNo to the Lot object. 

	FIXME: At this point, we don't have a provision for deleting lot
	objects that are not needed anymore, so this may cause something
	like a "memory leak" for a long simulation with a lot of lots.
    */
    static HashMap<Long, Lot> allLots = new HashMap<>();

    final long lotNo;

    final double manufacturingDate;
    /** The expiration date of this lot. If the product never expire,
	we store Double.POSITIVE_INFINITY here */
    final double expirationDate;

    static Lot get(long _lotNo) {
	return allLots.get(_lotNo);
    }

    private Lot(long _lotNo, double now, double _expirationDate) {
	lotNo = _lotNo;
	manufacturingDate = now;
	expirationDate = _expirationDate;	
    }

    /** Creates a lot with a specified ID number (which is expected to
	be unique), manufacturing date, and expiration date, and records
	it in the table of all lots.
     */
    static void registerLot(long lotNo, double now, double _expirationDate) {
	Lot x = new Lot( lotNo, now,  _expirationDate);
	allLots.put( new Long(lotNo), x);
    }

    /** Has this lot expired as of now? */
    boolean hasExpired(double now) {
	return now >=  expirationDate;
    }

}
