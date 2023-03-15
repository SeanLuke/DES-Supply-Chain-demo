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
public class EE extends Entity {
    static final String uname = "EE";
    static CountableResource uEE = new  CountableResource(uname,1);

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

    /** Information about the "individual features" of this EE device,
	such as the "plan for breakdowns". */
    static class EEInfo {
	double remainingLifetime;
	double remainingTbf;
	/** The clock time point at which the clocks decreasing the remainingLifetime and remainingTbf started. This is typically the time point when treatment was started or resumed. The value is null if at present the clock is not running (because the device is not in use with a patient). */
	Double useStartedAt=null;
	EEInfo(boolean doInit) {
	    if (doInit) init();
	}
	private void init() {
	    remainingLifetime =  Math.abs(lifetimeDistribution.nextDouble());
	    remainingTbf =  Math.abs(tbfDistribution.nextDouble());
	}
    }



    public EE(int n) {
	super(  "EE" );
	EEInfo pi = new EEInfo(true);
	setInfo( pi);
	setStorage( new Resource[] {giveUEE(n)});

    }
	

    
}
