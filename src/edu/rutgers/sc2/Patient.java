package  edu.rutgers.sc2;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.IllegalInputException;
import edu.rutgers.util.Config;
import edu.rutgers.util.ParaSet;
import edu.rutgers.supply.*;


/** Represents a patient, either a "naked" one (waiting for
    treatment), or one fitted with an EE and DS (being treated)
 */
public class Patient extends Entity {
    static CountableResource uPatient = new  CountableResource("Patient",1);
    //private static Batch prototype;
    static Patient prototype;

    static long idCnt = 0;
    
    static class PatientInfo {
	/** The date when the treatment started. The value is null until it has
	    started. */
	//Double treatmentStarted=null;
	/** The date when the treatment is scheduled to end. The value is
	    null until it has started. */
	//Double treatmentToEnd=null;

	/** This is initialized when the treatment starts the first
	    time; decremented if the treatment is interrupted. */
	Double treatmentTimeLeft=null;
		
	/** The attached EE, if any */
	EE ee=null;
	// DS ds=null;
	final long id = (idCnt++);
    }

    /** Should be called once, before any actual patients are created */
    public static void init(Config config ) throws IllegalInputException {	
	//prototype = mkPrototype(UPatient, config);
	prototype = new Patient(true);
    }

    /** Creates the prototype entity */
    private Patient(boolean proto) {
	super("Patient");
    }

    
    Patient() {
	super( prototype );
	PatientInfo pi = new PatientInfo();
	setInfo( pi);
	setStorage( new Resource[] {uPatient});
	//System.out.println("Created Prototype Batch = " +this);
    }

    PatientInfo getPatientInfo() {
	return (PatientInfo) getInfo();
    }

    EE getEE() {
	return getPatientInfo().ee;
    }

    /** Starts or resumes treatment, attaching an EE and DS to the
	patient, and setting or adjusting time counters in the Patient
	and the EE object */
    void startTreatment(double now, EE _ee,
			// DS ds, // FIXME
			AbstractDistribution serviceTimeDistribution) {
	startTreatment(now, _ee,  serviceTimeDistribution, 0, 0);
    }


    /**
       @param makeOlderBy Normally, it's 0. If a positive number is given, we're
       initializing a patient (at now=0) whose treatment was started a specified
       number of days ago. This is used to prepopulate the SPP on startup,
       simulating an established population of patients.
       @param addToAll Normally 0. When used from the initialization routine (at t=-1), this value is 1. This is kludge used to deal with the fact that MASON initialization runs at t=-1, but DES Delay won't like a negative "ripe" date.
      
       @return true on success, false on failure (the patient should be gone by
	now)
    */
    boolean startTreatment(double now, EE _ee,
			// DS ds, // FIXME
			AbstractDistribution serviceTimeDistribution,
			   double makeOlderBy, double addToAll
			) {
	PatientInfo pi = getPatientInfo();

	if (pi.ee!=null) throw new AssertionError("Patient already has an EE");
	pi.ee = _ee;

	if ( pi.treatmentTimeLeft == null) {
	    pi.treatmentTimeLeft = serviceTimeDistribution.nextDouble();
	    pi.treatmentTimeLeft -= makeOlderBy;
	    if (pi.treatmentTimeLeft <= 0) return false;
	    pi.treatmentTimeLeft += addToAll;
	}
	
	pi.ee.startUse(now, pi.treatmentTimeLeft, addToAll);
	return true;
    }



    
    public String toString() {
	String s = "Patient no. " +  getPatientInfo().id;
	EE ee = getEE();
	if (ee!=null) s += ", with EE=" + ee;
	return s;
    }
    
}
