package  edu.rutgers.supply;

import java.util.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;


/** A LotInfo object contains useful information about a lot of product
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

    /** Unique lot number */
    final long lotNo;

    /** The manufactuing date of this lot */
    double manufacturingDate;

    
    /** The expiration date of this lot. If the product never expire,
	we store Double.POSITIVE_INFINITY here */
    double expirationDate;

    /** The earliest of the dates on which the "ancestors" of this lot
	were manufactured, or, absent any, the lots own manufacturing
	date.  This is not needed in the actual simulation, but is
	used for flow time stats, so that e.g. we can trace the path
	of meat through the factory from a live cow to a can of spam.
    */
    public double getEarliestAncestorManufacturingDate() {
	return earliestAncestorManufacturingDate;
    }
	
    double earliestAncestorManufacturingDate;

    /** Some additional text info that may be stored in the lot. Used
	primarily for studying various fine properties of the supply
	chain, such as the distribution of batch ages.
     */
    String message = "";

    public void addToMsg(String s) {
	message +=  (message.length()>0?"; ":"") + s;
    }

    public void addToMsg(LotInfo precursor) {
	addToMsg("[Precursor: mfg="+precursor.manufacturingDate+"; "+ precursor.message + "]");
    }
     
    /** Some lots may have this value set to non-zero, e.g. as a result
	of a disruption that causes a deterioration of product quality */
    public double getIncreaseInFaultRate() { return increaseInFaultRate; }
    double increaseInFaultRate=0;
    public void setIncreaseInFaultRate(double _increaseInFaultRate) { increaseInFaultRate = _increaseInFaultRate; }


    public String toString() {
	String s = "(Lot No. "+lotNo+", made@" + manufacturingDate +", expire@"+expirationDate + (message.length()>0?"; "+message:"") +")";
	return s;
    }

    /** Used by sc2.Patient etc */
    //    protected LotInfo(long _lotNo) {
    //	lotNo = _lotNo;
    //    }
       
	
    private LotInfo(long _lotNo, double now, double _expirationDate, double _earliestAncestorManufacturingDate) {
	lotNo = _lotNo;
	manufacturingDate = now;
	expirationDate = _expirationDate;
	earliestAncestorManufacturingDate  = _earliestAncestorManufacturingDate;
    }

    /** Adjusts the various dates in this lot as a result of merger with
	another lot, the product in which may be not as fresh as in this lot.
     */
    void effectMerge(LotInfo other) {
	//long _lotNo, double now, double _expirationDate, double _earliestAncestorManufacturingDate) 
	manufacturingDate = Math.min(manufacturingDate, other.manufacturingDate);
	expirationDate = Math.min(expirationDate, other.expirationDate);
	earliestAncestorManufacturingDate  = Math.min(earliestAncestorManufacturingDate, other.earliestAncestorManufacturingDate);
    }



    
    /** Creates a the LotInfo object for a lot with a new unique ID number 
     */
    static LotInfo newLot(double now, double _expirationDate, double _earliestAncestorManufacturingDate) {
	LotInfo x = new LotInfo( nextLotNo(), now,  _expirationDate, _earliestAncestorManufacturingDate);
	return x;
    }

    /** Has this lot expired as of now? */
    boolean hasExpired(double now) {
	return now >=  expirationDate;
    }

    /** How many units in this batch are "illicit" */
    public double illicitCount=0;


    /** Creates the "label" for a lot into which part of the existing
	lot is to be separated */
    LotInfo split() {
	 LotInfo x =  new LotInfo( nextLotNo(), manufacturingDate,  expirationDate, earliestAncestorManufacturingDate);
	 return x;
    }

    
}
