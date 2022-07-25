package  edu.rutgers.pharma3;

import java.io.*;
import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import ec.util.MersenneTwisterFast;

import edu.rutgers.util.*;


import sim.portrayal.grid.*;

import sim.portrayal.network.*;
import sim.portrayal.continuous.*;
import sim.display.*;
import sim.portrayal.simple.*;
import sim.portrayal.*;
import javax.swing.*;
import java.awt.Color;
import java.awt.*;
import sim.field.network.*;
import sim.des.portrayal.*;


/** The main class for a  simple pharmaceutical supply chain simulation demo */
public class Demo extends SimState {

    static boolean verbose=false;

    public DES2D field = new DES2D(200, 200);
 
    
    void add(Steppable z) {
	IterativeRepeat ir =	schedule.scheduleRepeating(z);
    }
    
    public Demo(long seed)    {
	super(seed);
	System.out.println("Demo()");
    }
    
    HospitalPool hospitalPool;
    PharmaCompany pharmaCompany;
    public HospitalPool getHospitalPool() {	return hospitalPool;    }    
    public PharmaCompany getPharmaCompany() {	return pharmaCompany;    }

    /** The list of Macro objects, for help in the GUI */
    Macro[] listMacros() {
	return pharmaCompany.listMacros(); 
    }

    public String version = "2.000";
    
    /** Here, the supply network elements are added to the Demo object */
    public void start(){
	super.start();
	System.out.println("Demo.start");
     
	try {
	    // The chart directory
	    File logDir = new File("charts");
	    Charter.setDir(logDir);

	    
	    CountableResource drug = new CountableResource("PackagedDrug", 0);

	    String pcName = "PharmaCompany", hosName="HospitalPool";
	    Batch pacDrugBatch = Batch.mkPrototype(drug, config);
	    
	    //System.out.println("Demo: drugBatch = " + drugBatch  );
	    
	    hospitalPool = new HospitalPool(this, hosName,  config, pacDrugBatch);
	    add(hospitalPool);
	    pharmaCompany = new PharmaCompany(this, pcName, config, hospitalPool, pacDrugBatch);
	    add(pharmaCompany);
	    
	    hospitalPool.setOrderDestination(pharmaCompany);
	    //if (2*2  !=4) throw new IllegalInputException("test");

	    depict();
      
	    
    	} catch( IllegalInputException ex) {
	    System.out.println("Unable to create a model due to a problem with the configuration parameters:\n" + ex);
	    ex.printStackTrace(System.err);
	    System.exit(1);
	} catch(Exception ex) {
	    System.out.println("Exception:\n" + ex);
	    ex.printStackTrace(System.err);
	    System.exit(1);
	}
	final int CENSUS_INTERVAL=60;
	schedule.scheduleRepeating(new Reporter(), CENSUS_INTERVAL);
	System.out.println("Pharma3 DES/MASON simulation, ver=" + version +", config=" + config.readFrom);
	doReport("Start");
    }

    /** Set up our network for display purposes 
     */
    void depict() {
        field = new DES2D(1000, 700);

        //field.add(pharmaCompany, 200, 20);
        //field.add(hospitalPool, 400, 20);


	pharmaCompany.depict(field);
	    
        // Connect all objects with edges	
        field.connectAll();
    }
    
    public void	finish() {
	doReport("Finish");
	System.out.println("Closing logs");
	Charter.closeAll();
    }

    static class Reporter implements Steppable {
	public void step(SimState state) {
	    ((Demo)state).doReport("Report at t=" + state.schedule.getTime());
	}  
    }

    void doReport(String msg) {
	System.out.println("===== "+schedule.getTime() + ": " +
			   msg+" =================\n"
			   + report());
	System.out.println("================================================");
	
    }
    
    String report() {
	Vector<String> v= new Vector<>();

	v.add(hospitalPool.report());
	v.add(pharmaCompany.report());

	return String.join("\n", v);
    }
  
    
    /** The Config object contains the parameters for
	various supply chain elements, read from a
	config file
    */
    static Config config;


    static String[] processArgv(String[] argv) throws IOException, IllegalInputException
    {

	String confPath = "config/pharma3.csv";


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
	
	File f= new File(confPath);
	config  = Config.readConfig(f);

	return va.toArray(new String[0]);
	
    }
    
    /** Extracts a few command-line options we understand, and leaves
	the rest of them to MASON.
    */
    public static void main(String[] argv) throws IOException, IllegalInputException {

	argv = processArgv(argv);
	
	doLoop(Demo.class, argv);
	
	System.exit(0);
    }
    
}
