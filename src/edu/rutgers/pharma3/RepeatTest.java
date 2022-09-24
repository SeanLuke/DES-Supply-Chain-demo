package  edu.rutgers.pharma3;

import java.io.*;
import java.util.*;
//import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

//import ec.util.MersenneTwisterFast;

import edu.rutgers.util.*;
//import edu.rutgers.pharma3.Disruptions.Disruption;

//import sim.display.*;

/** This is a sample program that makes a large number of one-year
    runs, each one with its own Demo object. To achieve that,
    it subclasses Demo (as MyDemo) and MakesDemo (as MyMakesDemo).

 */
public class RepeatTest {

    /** An example of a customized class extending Demo */
    static class MyDemo extends Demo {
	/** So that it knows its number in the sequence of runs */
	int seqNo;
	public MyDemo(long seed)    {
	    super(seed);
	    if (verbose) System.out.println("pharma3.Demo()");
	}
  
	public void	start() {
	    System.out.println("Starting run No. " + seqNo);
	    super.start();
	}
	
	public void	finish() {
	    super.finish();
	    System.out.println("Finished run No. " + seqNo +"; distro.everReceived=" + getPharmaCompany().getDistributor().getEverReceived());
	}
    }

    /** In a real optizmiation program, this class would modify
	the config parameters of Demo each time newInstance() 
	is called */
    static class MyMakesDemo extends Demo.MakesDemo {
	int seqNo = 0;
	MyMakesDemo(String[] argv) throws IOException, IllegalInputException    {
	    super(argv);
	    // Turns of charting
	    Charter.setDir(null);
	    Demo.quiet = true;
	}
	public java.lang.Class	simulationClass() {
	    return MyDemo.class;
	}	    
	public java.lang.reflect.Constructor[]	getConstructors() {
	    return MyDemo.class.getConstructors();
	}
	/** This is called from doLoop before every repetition. A real optimization
	    program would somehow ensure that this method creates a Demo object
	    with modified parameters, rather than the same ones as before.
	*/
	public SimState	newInstance(long seed, java.lang.String[] args) {
	    MyDemo demo = new MyDemo(seed);
	    initDemo(demo);
	    demo.seqNo = (seqNo++);
	    return demo;
	}
    }


    /** This imitates an optimization program, except that it does not optimize anything.
	A real optimization program would, before calling doLoop(), pass some
	useful information to maker, so that the maker will create a Demo
	with different parameters the next time maker.newInstance() is invoked.
    */
    public static void main(String[] argv) throws IOException, IllegalInputException {

	MyMakesDemo maker = new MyMakesDemo(argv);
	argv = maker.argvStripped;

	MyDemo.doLoop(maker, argv);
	
	System.exit(0);
    }
    
}
