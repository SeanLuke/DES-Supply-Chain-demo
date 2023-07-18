package  edu.rutgers.test;

import edu.rutgers.util.*;
import edu.rutgers.supply.*;
import edu.rutgers.sc3.*;

import java.io.*;
import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

//import ec.util.MersenneTwisterFast;

//import edu.rutgers.util.*;
//import edu.rutgers.supply.Disruptions.Disruption;
import edu.rutgers.supply.Disruptions.Type;

/** An example of using sc3.Demo (the main class of the SC-3 simulation app)
    in an optimization-like context */
public class TestSc3 {

    /** Runs one simulation.  This method contains the most essential
	code form SimState.doLoop(), but without the final exit(), so
	that it can be run multiple times in the same program.

	@param state A fully configured Demo object (a model)
	@param until the number of time steps (days) to run the simulation for
    */
    public static void myLoop(SimState state, double until) {

	//state = generator.newInstance(seed,args);
	//                  state.job = job;
	//                  state.seed = seed;
	state.start();

	boolean retval = false;
	long steps = 0;
	long clock;
	long oldClock = System.currentTimeMillis();
	Schedule schedule = state.schedule;
	long firstSteps = schedule.getSteps();
                        
	while(schedule.getTime() <= until)	    {
	    state.preSchedule();
	    if (!schedule.step(state)) 		    {
		throw new AssertionError("Step failed");
	    }
	    state.postSchedule();
	}
                                
	state.finish();
    }

    static String[] nodes = {
	"prepregProd",
	"substrateSmallProd",
	"substrateLargeProd",
	"cellProd",
	"cellAssembly",
	"cellPackaging",
	"arraySmallAssembly"
    };

    
    static Type[] durational =
    {Type.Halt};

    
    /** Creates disruption scenario No. n on a specified node of the supply
	chain and with a specified disruption type.
     */
    private static Disruptions createDisruptionScenarioA(Type type, String node, int n) {
	Disruptions.setSc2BackwardCompatible(false);
	Disruptions h = new Disruptions();
	if (type!=null && node != null) {
	    double time = 100;
	    double duration = 300 * n;
	    double magnitude = 1;
	    h.add(type, node, time, duration, magnitude);
	}
	return h;
    }

    private static Disruptions createDisruptionScenarioB(Type type, String node, int n) {
	Disruptions h = new Disruptions();
	if (type!=null && node != null) {
	    double time = 100;
	    double duration = 1900;
	    double magnitude = 1.0 * (n+1);
	    h.add(type, node, time, duration, magnitude);
	}
	return h;
    }

    /**
       Try this:
       <ul>
       <li>With no disruptions (baseline), several times.
       <li>With disruption Type.Halt, for each node and for several levels of duration
       <li>With some other disruption type, ditto...
       <li>...
       </ul>
     */
    public static void main(String[] argv) throws IOException, IllegalInputException {

	Demo.MakesDemo maker = new Demo.MakesDemo(argv);
	argv = maker.argvStripped;

	long seed = 0;
	int cnt=0;

	/*
	//-- Baseline runs
	for(int k=1; k<= 10; k++) {
	    String label = "("+(cnt++)+") Baseline:" + k;
	    // Create a  disruption scenario
	    Disruptions disr = createDisruptionScenarioA(null, null, k);
	    test(argv, maker, disr, seed++, label);
	}
	*/

	//for(int m=0; m<=durational.length; m++) {
	

	Type type;

	
	type= Type.Halt;
	System.out.println("----  Disruption type " + type + " ----");		

	for(int j=0; j< nodes.length; j++) {
	    for(int k=1; k<= 3; k++) {
		String label =  "("+(cnt++)+") " + type +":" + nodes[j] + ":"+k;
		// Create a  disruption scenario
		Disruptions disr = createDisruptionScenarioA(type, nodes[j], k);
		test(argv, maker, disr, seed++, label);
	    }
	}
	
	
	type = Type.ProdDelayFactor;
	System.out.println("----  Disruption type " + type + " ----");

	for(int j=0; j< nodes.length; j++) {
	    for(int k=1; k<= 3; k++) {
		String label =  "("+(cnt++)+") " + type +":" + nodes[j] + ":" + k;
		// Create a  disruption scenario
		Disruptions disr = createDisruptionScenarioB(type, nodes[j], k);
		test(argv, maker, disr, seed++, label);
	    }
	}

	
	System.exit(0);

    }
    
    /** Wrapper for a single simulation run */
    private static void test(String[] argv,
			     Demo.MakesDemo maker,
			     Disruptions disr, long seed, String label) {
	final double until = 2000;

	Demo demo = (Demo)maker.newInstance( seed, argv);   
	//demo.setQuiet( true);
	demo.setDisruptions(disr);
		
	// Run simulation
	System.out.println("Run No. " + label + ", seed=" + seed+"; Disruptions=" + disr);
	    
	myLoop( demo, until);
		
	EndCustomer.Stats[] stats = demo.getWaitingStats();
       	EndCustomer.Stats awf=stats[0], awu=stats[1], aw = stats[2];
	
	Vector<String> v = new Vector<>();
	
	if (awf.cnt>0) 	v.add( " for "+awf.cnt+" filled orders " + awf.avgT   + " days");
	if (awu.cnt>0) 	v.add( " for "+awu.cnt+" unfilled orders " + awu.avgT  + " days so far");     
	if (aw.cnt>0) 	v.add( " for all "+aw.cnt+" orders " + aw.avgT     + " days so far");
	
	String s = 
	    (v.size()>0) ? "Avg waiting time" + String.join(",", v) + ".":
	    "No orders!";
			
	System.out.println(s);
    }

    
    
}


