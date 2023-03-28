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


/** An object of this class can represent either a single fully initialized EE,
    or a batch of manufactured, but not initialized EE units. */
public class EE extends Batch
			//		       Entity
{
    static final String uname = "EE";
    static CountableResource uEE = new CountableResource(uname,1);

    private static  CountableResource giveUEE(int n) {
	if (n<1) throw new IllegalArgumentException();
	else if (n==1) return uEE;
	else return new  CountableResource(uname,n);
    }
    
    private static  AbstractDistribution lifetimeDistribution, tbfDistribution;
 

    /** Should be called once, before any actual patients are created */
    public static void init(SimState state, Config config ) throws IllegalInputException {
	//prototype = mkPrototype(UEE, config);


	ParaSet para = config.get(uname);
	if (para==null) throw new  IllegalInputException("No config parameters specified for '" + uname +"' in config file read from " + config.readFrom);
	lifetimeDistribution = para.getDistribution("lifetime",state.random);
	tbfDistribution = para.getDistribution("tbf", state.random);
    }

    enum EndCode { PATIENT_CURED, EE_DIED, EE_BROKEN };

    boolean patientCured() {
	return getEEInfo().endCode==EndCode.PATIENT_CURED;
    }

    boolean eeDied() {
	return getEEInfo().endCode==EndCode.EE_DIED;
    }

    boolean eeBroken() {
	return getEEInfo().endCode==EndCode.EE_BROKEN;
    }

    
    /** Information about the "individual features" of this EE device,
	such as the "plan for breakdowns". */
    static class EEInfo extends LotInfo {
	double remainingLifetime;
	double remainingTbf;
	/** The clock time point at which the clocks decreasing the remainingLifetime and remainingTbf started. This is typically the time point when treatment was started or resumed. The value is null if at present the clock is not running (because the device is not in use with a patient). */
	Double useStartedAt=null;
	EEInfo(LotInfo li) {
	    super(li);
	    init();
	}
	private void init() {
	    remainingLifetime =  Math.abs(lifetimeDistribution.nextDouble());
	    remainingTbf =  Math.abs(tbfDistribution.nextDouble());
	}

	/** How much time this device will spend with a patient this time */
	double delayTime;

	/** How the patient's experience with this device will end */
	EndCode endCode = null;

	/** After the device is separated from the patient, will the patient
	    still need more treatment? (This is 0 in a normal situaion,
	    and positive in the case of a device breakdown) */
	double remainingTreatmentTime;
	
	/** Attach this EE device to a patient who is being sent to
	    treatment now. This method is called at the moment when
	    the EE device is attached to the patient, at the start of the
	    treatment.
	*/
	void startUse(double now, double treatmentTime) {
	    useStartedAt = now;
	    endCode = EndCode.PATIENT_CURED;
	    delayTime = treatmentTime;
	    if (remainingLifetime <= remainingTbf) {
		if (remainingLifetime <  treatmentTime) {
		    endCode = EndCode.EE_DIED;
		    delayTime = remainingLifetime;
		}
	    } else {
		if (remainingTbf <   treatmentTime) {
		    endCode = EndCode.EE_BROKEN;
		    delayTime = remainingTbf;
		}
	    }
	    remainingTreatmentTime = treatmentTime - delayTime;
	}

	/** This must be called when the device is separated from the
	    patient, either due to the successful end of the
	    treatment, or the breakdown of the device. It adjusts time
	    counters in the device and in the patient. */
	void finishUse(Patient p) {
	    p.getPatientInfo().treatmentTimeLeft -= delayTime;	    
	    remainingLifetime -= delayTime;
	    remainingTbf -= delayTime;
	    useStartedAt = null;
	    delayTime = 0;
	    final double eps = 1e-6;
	    if (remainingTbf <= eps) {
		remainingTbf =  Math.abs(tbfDistribution.nextDouble());
	    }
	}


	public String toString() {
	    return "[EE: code=" + endCode +", life left=" +   remainingLifetime +
		", TBF left=" + remainingTbf +"; use started at " + useStartedAt +", delay=" + delayTime + "]";
	}
	
    }

    

    /*
    public EE(int n) {
	super(  "EE" );
	EEInfo pi = new EEInfo(true);
	setInfo( pi);
	setStorage( new Resource[] {giveUEE(n)});

    }
    */

    /** Converts a plain Batch of 1 uEE to a proper EE object, by adding
	individualized EE-specific information.
     */
    public EE(Batch b) {
	super(b, b.getLot(), b.getContentAmount());
	if (!b.getUnderlying().isSameType(uEE) ||
	    b.getContentAmount()!=1 ||
	    !(getInfo() instanceof LotInfo)) throw new IllegalArgumentException("Cannot convert " + b + " to EE");
	setInfo( new EEInfo(b.getLot()));
	
    }

    EEInfo getEEInfo() {
	return (EEInfo)getInfo();
    }

    void startUse(double now, double treatmentTime) {
	getEEInfo().startUse( now, treatmentTime);

    }
    
    void finishUse(Patient p) {
	getEEInfo().finishUse(p);
	p.getPatientInfo().ee = null;
    }
    
    public String toString() {
	return getEEInfo().toString();
    }
}
