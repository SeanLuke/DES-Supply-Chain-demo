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

public class LotInfo {

    /** Used to generate unique sequential lot numbers */
    private static long lotNoGen = 0;

    /** Generates a unique lot number, which then can be assigned to a new lot */
    private static long nextLotNo() {
	return ++lotNoGen;
	
    }

    final long lotNo;

    final double manufacturingDate;
    /** The expiration date of this lot. If the product never expire,
	we store Double.POSITIVE_INFINITY here */
    final double expirationDate;

    /** Some lots may have this value set to non-zero, e.g. as a result
	of a disruption that causes a deterioration of product quality */
    double increaseInFaultRate=0;

    public String toString() {
	return "(Lot No. "+lotNo+", made@" + manufacturingDate +", expire@"+expirationDate+")";
    }
       
	
    private LotInfo(long _lotNo, double now, double _expirationDate) {
	lotNo = _lotNo;
	manufacturingDate = now;
	expirationDate = _expirationDate;	
    }

    /** Creates a the LotInfo object for a lot with a new unique ID number 
     */
    static LotInfo newLot(double now, double _expirationDate) {
	LotInfo x = new LotInfo( nextLotNo(), now,  _expirationDate);
	return x;
    }

    /** Has this lot expired as of now? */
    boolean hasExpired(double now) {
	return now >=  expirationDate;
    }

    /** How many units in this batch are "illicit" */
    double illicitCount=0;
    
}
