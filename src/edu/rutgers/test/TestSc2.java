package  edu.rutgers.test;

import edu.rutgers.util.*;
import edu.rutgers.supply.*;
import edu.rutgers.sc2.*;

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

/** An example of using sc2.Demo (the main class of the SC-2 simulation app)
    in an optimization-like context */
public class TestSc2 {

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


    /** Creates disruption scenario No. n
     */
    private static Disruptions createDisruptionScenario(int n) {
	Disruptions h = new Disruptions();
	Disruptions.Type type = Disruptions.Type.Halt;
	String unit = "eeCmoProd";
	double time = 100;
	double magnitude = 200 * n;
	h.add(type, unit, time, magnitude);	
	return h;
    }

    
    public static void main(String[] argv) throws IOException, IllegalInputException {

	Demo.MakesDemo maker = new Demo.MakesDemo(argv);
	argv = maker.argvStripped;
	long seed = 0;
	double until = 2000;



	for(int j=0; j< 10; j++) {
	    // Create a blank model
	    Demo demo = (Demo)maker.newInstance( seed, argv);	    	    
	    demo.setQuiet( true);
	    // Set disruptions
	    Disruptions disr = createDisruptionScenario(j);
	    demo.setDisruptions(disr);

	    // Run simulation
	    System.out.println("Run No. " + j +"; Disruptions=" + disr);
	    
	    myLoop( demo, until);
	    double avgWaiting = demo.wpq.sumWaiting/ demo.wpq.nWaiting;
	    double finalWaiting = demo.wpq.getAvailable();

	    System.out.println("Result: Avg waiting list length through the simulation=" + avgWaiting +"; Final length=" + finalWaiting);


	}

       	
	System.exit(0);
    }
    
    
}


