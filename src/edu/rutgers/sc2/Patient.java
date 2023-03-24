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


public class Patient extends Entity {
    static CountableResource uPatient = new  CountableResource("Patient",1);
    //private static Batch prototype;
    static Patient prototype;

    static class PatientInfo {
	/** The date when the treatment started. The value is null until it has
	    started. */
	Double treatmentStarted=null;
	/** The date when the treatment is scheduled to end. The value is
	    null until it has started. */
	Double treatmentToEnd=null;

	/** The attached EE, if any */
	EE ee=null;
	// DS ds=null;
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

    
    
}
