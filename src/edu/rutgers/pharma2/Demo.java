package  edu.rutgers.pharma2;

import java.io.*;
import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import ec.util.MersenneTwisterFast;

import edu.rutgers.util.*;

/** The main class for a  simple pharmaceutical supply chain simulation demo */
public class Demo extends SimState {

    static boolean verbose=false;
    
    void add(Steppable z) {
	//allPersons.add(z);
	
	IterativeRepeat ir =	schedule.scheduleRepeating(z);
	// z.repeater=ir;
    }

    
    public Demo(long seed)    {
	super(seed);
    }
    HospitalPool hospitalPool;
    PharmaCompany pharmaCompany;

    

    /** Here, the supply network elements are added to the Demo object */
    public void start(){
	super.start();

	try {

	    CountableResource drug = new CountableResource("packagedDrug", 0);
	    
	    hospitalPool = new HospitalPool(this, "HospitalPool",  config,drug);
	    add(hospitalPool);
	    pharmaCompany = new PharmaCompany(this, "PharmaCompany", config, hospitalPool);
	    add(pharmaCompany);
	    
	    hospitalPool.setOrderDestination(pharmaCompany);

	    
    	} catch( IllegalInputException ex) {
	    System.out.println("Unable to create a model due to a problem with the configuration parameters:\n" + ex);
	    System.exit(1);
	}
	final int CENSUS_INTERVAL=60;
	schedule.scheduleRepeating(new Reporter(), CENSUS_INTERVAL);
	doReport("Start");
    }


   public void	finish() {
	doReport("Finish");
    }

    static class Reporter implements Steppable {
	public void step(SimState state) {
	    ((Demo)state).doReport("Report at t=" + state.schedule.getTime());
	}  
    }

    void doReport(String msg) {
	System.out.println("===== "+msg+" ===================================\n"
			   + report());
	System.out.println("================================================");
	
    }
    
    String report() {
	Vector<String> v= new Vector<>();

	v.add(hospitalPool.report());
	v.add(pharmaCompany.report());

	
	/*
	for(IngredientStorage q: ingStore) v.add(q.report());
	v.add(packmatStore.report());
	for(PreprocStorage q: preprocStore) v.add(q.report());
	v.add(testedPackmatStore.report());
	v.add(production.report());
	v.add("[PostProcStore has " + postprocStore.getAvailable() + " units]");
	v.add(packaging.report());
	v.add( dispatchStore.report());
	*/
	return String.join("\n", v);
    }
  


    
    /** The Config object contains the parameters for
	various supply chain elements, read from a
	config file
    */
    private static Config config;

    /** Unit test for Binomial */
    public static void main2(String[] argv)      {
	    if (argv.length != 2)
	    	throw new IllegalArgumentException("Usage: Binomial n p");
	    	
	    int n=Integer.parseInt(argv[0]);
	    double p=Double.parseDouble(argv[1]);
	    MersenneTwisterFast random = new MersenneTwisterFast();
	    //Triangular t = new Triangular(min, mode, max, random);
	    Binomial t = new Binomial(n, p, random);
	    System.out.println(t);
	    for(int j=0; j<10; j++) 
	    {
		for(int k=0; k<10; k++) {
		    System.out.print(t.nextInt() + " ");
		}
		System.out.println();

	    }
    }

    
    
      /** Extracts a few command-line options we understand, and leaves
	the rest of them to MASON.
    */
    public static void main(String[] argv) throws IOException, IllegalInputException {
	//main2(argv);	System.exit(0);
 	       	
	
	String confPath = "config/pharma2.csv";

	Vector<String> va = new Vector<String>();
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    if (a.equals("-verbose")) {
		verbose = true;
	    } else if (a.equals("-config") && j+1<argv.length) {
		confPath= argv[++j];
	    } else {
		va.add(a);
	    }
	}

	argv = va.toArray(new String[0]);
	
	File f= new File(confPath);
	config  = Config.readConfig(f);
	
	
	doLoop(Demo.class, argv);
	
	System.exit(0);
    }


}
