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
	null,
	"prepregProd",
	"substrateSmallProd",
	"substrateLargeProd",
	"cellProd",
	"cellAssembly",
	"cellPackaging",
	"arraySmallAssembly"
    };


    /** Creates disruption scenario No. n on unit No, j
     */
    private static Disruptions createDisruptionScenario(int j, int n) {
	Disruptions h = new Disruptions();
	Disruptions.Type type = Disruptions.Type.Halt;
	String unit = nodes[j];
	if (unit != null) {
	    double time = 100;
	    double magnitude = 200 * n;
	    h.add(type, unit, time, magnitude);
	}
	return h;
    }

    
    public static void main(String[] argv) throws IOException, IllegalInputException {

	Demo.MakesDemo maker = new Demo.MakesDemo(argv);
	argv = maker.argvStripped;
	double until = 2000;

	long seed = 0;


	for(int j=0; j< nodes.length; j++) {
	    for(int k=1; k<= 10; k++) {
		Demo demo = (Demo)maker.newInstance( seed, argv);	    	    
		demo.setQuiet( true);
		// Set disruptions
		Disruptions disr = createDisruptionScenario(j, k);
		demo.setDisruptions(disr);
		
		// Run simulation
		System.out.println("Run No. " + j +":" + k + ", seed=" + seed+"; Disruptions=" + disr);
	    
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
		seed++;
	    }

	}

       	
	System.exit(0);
    }
    
    
}


