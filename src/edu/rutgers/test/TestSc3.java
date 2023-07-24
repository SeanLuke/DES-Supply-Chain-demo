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


//import edu.rutgers.util.*;
//import edu.rutgers.supply.Disruptions.Disruption;
import edu.rutgers.supply.Disruptions.Type;

/** This application presents an example of using sc3.Demo (the main
    class of the SC-3 simulation app) in an optimization-like
    context.

    <p>
    First, it runs the simulation several times in baseline mode (with
    no disruoptions). Then it carries out a number of runs in each of which
    one particular disruption is applied to one particular node or link
    of the supply chain.

    <P>For each run, the "summary numbers" are printed, showing the
    [weighted] average waiting time for solar panel assemblies. Any of them
    (esp. the weighted avg waiting time for all orders throughout the run) can
    be used as the objective function in an actual optimization.

    <p>
    You may want to run it with the -quiet command line option in order for
    each run to print only the description of the scenario and the summary
    numbers, instead of detailed info pages.

    <p>The disruption types are supported as follows:

<pre>
* Halt, applied to the name of a production unit -- for most production notes. This was not called for in RBS's PowerPoint
document, but is a traditional easy-to-understand disruption type.


* ProdDelayFactor, applied to the name of a production unit =
"Disrupted Production" (disruption of production by changing the delay
distribution of a product). This covers some, but not all of disruptions p-1 throgh p-13. The disruptions we cover are those that affect internal manufacturing units (such as 	"prepregProd",   p-3), but not those on extrenal suppliers (e.g. Fiber Supplier Pool, p-1), because the parameter file makes the latter essentially "magic", without their own processing times, capacities, etc. For the latter units, the only way to affect them is through the  "Disrupted Replenishment Lead Time", below.

* (Not supported: Disrupted Testing t-1 through t-4. Since testing is usually directly downstream from production, the experimenter can just concentrate on the latter).

* TransDelayFactor, applied to prodUnitName.inputName (e.g. 	    "prepregProd.fiber") = "Disrupted Replenishment Lead Time" in the RBS table (r-1 thru r-8) and "Disrupted Transportation Time" for the same link

* TransDelayFactor, applied to prodUnitName = "Disrupted Transportation Time" for the link from this production unit (or production/QA combo) to the next node of the chain

* Adulteration, applied to the production unit that have non-zero failure rates. They increase the failure rate by the stated absolute amount. (E.g. the magnitude of 0.2 may increase the failure rate from 0.05 to 0.25). The effect of these disruptions, by themselves, is not that significant, because the model has builtin compensation (if some units of product fail inspection, more units are started, and orders for more raw material etc are sent out). Thus, for example, increasing the failure rate form near 0 to 0.5 means, basically, that it takes twice as much time to produce the required number of good units.

</pre>




*/
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

    
    /** Creates disruption scenario No. n on a specified node of the supply
	chain and with a specified disruption type. This is for
	"durational" disruptions (such as Halt), where the
	magnitude is constant (1), and we vary the duration.
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

    /** This is for "magnitudinal" disruptions, where we can fix the duration
	and vary the magnitude. E.g. various "slowdown"-type disruptions.
     */
    private static Disruptions createDisruptionScenarioB(Type type, String node, int n) {
	Disruptions h = new Disruptions();
	if (type!=null && node != null) {
	    double time = 100;
	    double duration = 1000;
	    double m = 1; //node.startsWith("cell") ? 5: 2;
	    double magnitude = m*(n+1)*(n+1)*(n+1);
	    h.add(type, node, time, duration, magnitude);
	}
	return h;
    }

    /** For Adulteration */
    private static Disruptions createDisruptionScenarioC(Type type, String node, int n) {
	Disruptions h = new Disruptions();
	if (type!=null && node != null) {
	    double time = 100;
	    double duration = 1000;
	    double m = 0.3;
	    double magnitude = m*n;
	    h.add(type, node, time, duration, magnitude);
	}
	return h;
    }

    
    /**
       This program carries out a number of simulation runs, as follows:
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

	//-- Baseline runs
	for(int k=1; k<= 3; k++) {
	    String label = "("+(cnt++)+") Baseline:" + k;
	    // Create a  disruption scenario
	    Disruptions disr = createDisruptionScenarioA(null, null, k);
	    test(argv, maker, disr, seed++, label);
	}

	Type type;

	//-- Halt
	type= Type.Halt;
	System.out.println("----  Disruption type " + type + " ----");		


	// Nodes we cover among p-1 thru p-13 
	String[] nodes = {
	    "prepregProd",   // p-3
	    "substrateSmallProd", // p-5
	    "substrateLargeProd", // the other face of p-5.
	    "cellProd", // p-7
	    "cellAssembly", // p-9
	    "cellPackaging",
	    "arraySmallAssembly", // p-11
	    "arrayLargeAssembly" // p-11
	};

	for(int j=0; j< nodes.length; j++) {
	    for(int k=1; k<= 3; k++) {
		String label =  "("+(cnt++)+") " + type +":" + nodes[j] + ":"+k;
		// Create a  disruption scenario
		Disruptions disr = createDisruptionScenarioA(type, nodes[j], k);
		test(argv, maker, disr, seed++, label);
	    }
	}
	
	//--- Production delays
	// Some of the p-1 thru p-13
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

	//---- Replenishment or transportation delays
	// "Disrupted Replenishment Lead Time" affects supplies of raw
	// materials (via MTS or MTO mechanism).  However, in our
	// model in many cases it is difficult to make a distincton
	// between "transportation" and "replenishment lead time", because
	// many sources (e.g. fiber) are described by a single number
	// ("replenishment lead time", or some such).
	String[] units = {
	    //-- Affecting replenishment times / input delays for the following input buffers
	    "arraySmallAssembly.adhesive", // r-6 and tr-7
	    "arraySmallAssembly.cell", // tr-11
	    "arraySmallAssembly.diode", // r-7 and tr-8
	    "cellPackaging.cellPM", // r-5 and tr-9
	    "cellProd.cellRM", // r-8 and tr-5
	    "cellProd.coverglass", // tr-6
	    "prepregProd.fiber",  // r-1  and tr-1
	    "prepregProd.resin",  // r-2  and tr-2
	    "substrateSmallProd.aluminum", // r-4 and tr-3
	    //-- Affecting output delays from the following production units
	    "prepregProd", // r-3 and tr-4
	    "substrateSmallProd", // tr-10
	    "substrateLargeProd", // tr-10
	    "cellProd",
	    "cellAssembly",
	    "cellPackaging",
	    "arraySmallAssembly", // tr-12
	    "arrayLargeAssembly", // tr-12
	};
	type = Type.TransDelayFactor;
	System.out.println("----  Disruption type " + type + " ----");

	for(int j=0; j< units.length; j++) {
	    System.out.println();
	    for(int k=1; k<= 3; k++) {
		String label =  "("+(cnt++)+") " + type +":" + units[j] + ":" + k;
		// Create a  disruption scenario
		Disruptions disr = createDisruptionScenarioB(type, units[j], k);
		test(argv, maker, disr, seed++, label);
	    }
	}

	//--- Adulteration
	String[] nodesC = {
	    "prepregProd",  // q-1
	    "substrateSmallProd", // q-2.1
	    "substrateLargeProd", // q-2.1
	    "cellAssembly",  // q-3
	    "arraySmallAssembly", // q-4.1
	    "arrayLargeAssembly", // q-4.1
	};

	type = Type.Adulteration;
	System.out.println("----  Disruption type " + type + " ----");

	for(int j=0; j< nodesC.length; j++) {
	    System.out.println();
	    for(int k=1; k<= 3; k++) {
		String label =  "("+(cnt++)+") " + type +":" + nodesC[j] + ":" + k;
		// Create a  disruption scenario
		Disruptions disr = createDisruptionScenarioC(type, nodesC[j], k);
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
	demo.setQuiet( true);
	demo.setDisruptions(disr);
		
	// Run simulation
	System.out.println("Run No. " + label + ", seed=" + seed+"; Disruptions=" + disr);
	System.out.println(disr.toCsv());
	    
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


