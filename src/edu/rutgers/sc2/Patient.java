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
	PatientInfo pi = getPatientInfo();
	if (pi.ee!=null) throw new AssertionError("Patient already has an EE");
	pi.ee = _ee;

	if ( pi.treatmentTimeLeft == null) {
	    pi.treatmentTimeLeft = serviceTimeDistribution.nextDouble();
	}
	pi.ee.startUse(now, pi.treatmentTimeLeft);
    }

    public String toString() {
	String s = "Patient no. " +  getPatientInfo().id;
	EE ee = getEE();
	if (ee!=null) s += ", with EE=" + ee;
	return s;
    }
    
}
