package  edu.rutgers.pharma3;

import edu.rutgers.supply.*;

import java.io.*;
import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import ec.util.MersenneTwisterFast;

import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;

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

    /** Set this to true to print a lot of stuff */
    static boolean verbose=false;
    /** Set this to true to print less stuff, and turn off all interactive things */
    static boolean quiet=false;

    public DES2D field = new DES2D(200, 200);

    /** Should be set by MakeDemo.newInstance(), and then used in start() */
    protected Config config=null;
    protected Disruptions disruptions = null;
    Vector<Disruption> hasDisruptionToday(Disruptions.Type type, String unit) {
	if (disruptions == null) return new Vector<Disruption>();
	double time = schedule.getTime();
	return disruptions.hasToday(type, unit, time);
    }


    /** Used to look up supply chain elements by name */
    private HashMap<String,Steppable> addedNodes = new HashMap<>();
    //private HashMap<String,Object> addedNodes = new HashMap<>();
    Steppable lookupNode(String name) { return addedNodes.get(name); }
    
    private int ordering = 0;
    void add(Steppable z) {
	if (z instanceof Named) {
	    addedNodes.put(((Named)z).getName(), z);
	}
	IterativeRepeat ir =	schedule.scheduleRepeating(z, ordering++, 1.0);
    }
    
    public Demo(long seed)    {
	super(seed);
	if (verbose) System.out.println("pharma3.Demo()");
    }
    
    Pool hospitalPool;
    PharmaCompany pharmaCompany;
    public Pool getHospitalPool() {	return hospitalPool;    }    
    public PharmaCompany getPharmaCompany() {	return pharmaCompany;    }

    UntrustedPool untrustedPool;
    Pool wholesalerPool;

    
    /** The list of Macro objects, for help in the GUI */
    Macro[] listMacros() {
	return pharmaCompany.listMacros(); 
    }

    public String version = "2.012";

    EndConsumer endConsumer;
    public EndConsumer getEndConsumer() {return  endConsumer;    }


    /** Here, the supply network elements are added to the Demo object */
    public void start(){
	super.start();
	if (!quiet) System.out.println("Demo.start");
	if (!quiet) System.out.println("Disruptions=" + disruptions);
	initSupplyChain();
	final int CENSUS_INTERVAL=360;
	if (verbose) schedule.scheduleRepeating(new Reporter(), CENSUS_INTERVAL);
	System.out.println("Pharma3 DES/MASON simulation, ver=" + version +", config=" + config.readFrom);
	if (verbose) doReport("Start");
    }

    /** The main part of the start() method. It is taken into a separate
	method so that it can also be used from auxiliary tools, such as 
	GraphAnalysis.
    */
    void initSupplyChain() {
	try {
	    
	    CountableResource drug = new CountableResource("PackagedDrug", 0);

	    String pcName = "PharmaCompany", hosName="HospitalPool";
	    Batch pacDrugBatch = Batch.mkPrototype(drug, config);	   

	    //System.out.println("Demo: drugBatch = " + drugBatch  );
	    
	    hospitalPool = new Pool(this, hosName,  config, pacDrugBatch);
	    add(hospitalPool);
	    pharmaCompany = new PharmaCompany(this, pcName, config, hospitalPool, pacDrugBatch);
	    add(pharmaCompany);
	    
	    //hospitalPool.setOrderDestination(pharmaCompany);
	    //if (2*2  !=4) throw new IllegalInputException("test");


	    endConsumer = new EndConsumer(this, "EndConsumer", config, pacDrugBatch);
	    endConsumer.setSource(hospitalPool);
	    add(endConsumer);

	    wholesalerPool = new Pool(this, "WholesalerPool", config, pacDrugBatch);
	    add(wholesalerPool);

	    untrustedPool = new UntrustedPool(this, "UntrustedPool", config, pacDrugBatch);
	    add(untrustedPool);
	    

	    //endConsumer.setSuppliers(addedNodes);
	    hospitalPool.setSuppliers(addedNodes);
	    wholesalerPool.setSuppliers(addedNodes);
	    
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
    }
    
    /** Set up our network for display purposes 
     */
    void depict() {
        //field = new DES2D(1000, 700);
	field = null;
	if (field==null) return;
	

        //field.add(hospitalPool, 400, 20);


	pharmaCompany.depict(field);
	    
        // Connect all objects with edges	
        field.connectAll();
    }
    
    public void	finish() {
	if (!quiet) doReport("Finish");
	if (verbose) System.out.println("Closing logs");
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

	v.add(endConsumer.report());
	v.add(hospitalPool.report());
	v.add(wholesalerPool.report());
	v.add(untrustedPool.report());
	v.add(pharmaCompany.report());

	return String.join("\n", v);
    }
  

    static class MakesDemo implements  MakesSimState {
   
	/** The Config object contains the parameters for
	    various supply chain elements, read from a
	    config file
	*/
	final private Config config0;
	final private Disruptions disruptions0;
	/** The data from the command line argument array, after the removal of options
	    interpreted by the constructor (such as -config XXX) will be put here. */
	final String[] argvStripped;

	/** For use in RepeatTest */
	//	int repeat=1;
	
	/** Initializes the Config and Disruptions structures from their respective
	    config files. 
	    @param argv The actual command line array. The constructor will look for
	    the -config and -disrupt options in it.
	 */
	MakesDemo(String[] argv) throws IOException, IllegalInputException    {

	    String confPath = "config/pharma3.csv";
	    String disruptPath = null;
	    String chartsPath = "charts";

	    Vector<String> va = new Vector<String>();
	    for(int j=0; j<argv.length; j++) {
		String a = argv[j];
		if (a.equals("-verbose")) {
		    verbose = true;
		} else if (a.equals("-config") && j+1<argv.length) {
		    confPath= argv[++j];
		} else if (a.equals("-disrupt") && j+1<argv.length) {
		    disruptPath= argv[++j];
		} else if (a.equals("-charts") && j+1<argv.length) {
		    chartsPath= argv[++j];
		    //} else if (a.equals("-repeat") && j+1<argv.length) {
		    // repeat = Integer.parseInt(argv[++j]);
		} else {
		    va.add(a);
		}
	    }
	
	    File f= new File(confPath);
	    config0  = Config.readConfig(f);

	    disruptions0 = (disruptPath == null) ? null:
		 Disruptions.readList(new File(disruptPath));

	    // The chart directory. (The value "null" means no charting)
	    File logDir = (chartsPath.equals("/dev/null") ||
			   chartsPath.equals("null")) ? null:
		new File(chartsPath);
	    Charter.setDir(logDir);

	    
	    argvStripped = va.toArray(new String[0]);
	    
	}


	public java.lang.Class	simulationClass() {
	    return Demo.class;
	}	    
	public java.lang.reflect.Constructor[]	getConstructors() {
	    return Demo.class.getConstructors();
	}

	protected void initDemo(Demo demo) {
	    //demo.disruptions = new Disruptions();
	    //demo.disruptions.add( Disruptions.Type.ShipmentLoss, "RawMaterialSupplier", 40, 30);
	    demo.config = config0;
	    demo.disruptions = disruptions0;
	}
	
	public SimState	newInstance(long seed, java.lang.String[] args) {
	    Demo demo = new Demo(seed);
	    initDemo(demo);
	    return demo;
	}
    }
    
    /** Extracts a few command-line options we understand, and leaves
	the rest of them to MASON.
    */
    public static void main(String[] argv) throws IOException, IllegalInputException {

	MakesDemo maker = new MakesDemo(argv);
	argv = maker.argvStripped;

	//doLoop(Demo.class, argv);
	doLoop(maker, argv);
	
	System.exit(0);
    }
    
}
