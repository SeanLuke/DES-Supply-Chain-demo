package  edu.rutgers.sc3;

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


/** The main class for the SC-3 model

<pre>
Demo demo = ....;
run simulation
</pre>
 */
public class Demo extends SimState {

    public String version = "1.000";

    
    /** Set this to true to print a lot of stuff */
    static boolean verbose=false;

    /** Set this to true to print less stuff, and turn off all interactive things */
    static boolean quiet=false;
    public void setQuiet(boolean _quiet) { quiet = _quiet; }
    
    public DES2D field = new DES2D(200, 200);

    /** Should be set by MakeDemo.newInstance(), and then used in start() */
    protected Config config=null;
    protected Disruptions disruptions = null;
    /** Sets the disruption scenario for this model */
    public void setDisruptions(Disruptions _disruptions) {
	disruptions = _disruptions;
    }

    
    Vector<Disruption> hasDisruptionToday(Disruptions.Type type, String unit) {
	if (disruptions == null) return new Vector<Disruption>();
	double time = schedule.getTime();
	return disruptions.hasToday(type, unit, time);
    }


    /** Used to look up supply chain elements by name */
    private HashMap<String,Steppable> addedNodes = new HashMap<>();
    Steppable lookupNode(String name) { return addedNodes.get(name); }

    Vector<Reporting> reporters = new Vector<>();

    
    private int ordering = 0;
    void add(Steppable z) {
	if (z instanceof Named) {
	    String name = ((Named)z).getName();
	    if (addedNodes.put(name, z)!=null) throw new IllegalArgumentException("Attempt to add duplicate node named " + name);
	}
	if (z instanceof Reporting) reporters.add((Reporting)z);
	IterativeRepeat ir =	schedule.scheduleRepeating(z, ordering++, 1.0);
    }

    void addFiller(String text) {
	reporters.add(new Filler(text));
    }
    
    public Demo(long seed)    {
	super(seed);
	if (verbose) System.out.println("sc3.Demo()");
    }

  /** Here, the supply network elements are added to the Demo object */
    public void start(){
	super.start();
	if (!quiet) System.out.println("Demo.start");
	if (!quiet) System.out.println("Disruptions=" + disruptions);
	initSupplyChain();
	final int CENSUS_INTERVAL=360;
	if (verbose) schedule.scheduleRepeating(new Reporter(), CENSUS_INTERVAL);
	if (!quiet) System.out.println("SC3 DES/MASON simulation, ver=" + version +", config=" + config.readFrom);
	if (verbose) doReport("Start");
    }

    //-- Not needed: implemented as SS source
    // Production 	fiberSupplier, resinSupplier;
    Production aluminumSupplier, cellPMSupplier,
	cellRMSupplier, coverglassSupplier;
    Production 	adhesiveSupplier, diodeSupplier;


    Production prepregProd, substrateProd, cellProd, cellCoverglassAssembly, cellPackaging;
    Production arrayAssembly;
    
 
    Batch fiberBatch, resinBatch, prepregBatch, aluminumBatch;
    Batch2 substrateBatch;
    Batch cellBatch, packagedCellBatch, cellRMBatch, coverglassBatch, coverglassAssemblyBatch, diodeBatch, adhesiveBatch;

    /** The main part of the start() method. It is taken into a separate
	method so that it can also be used from auxiliary tools, such as 
	GraphAnalysis.
    */
    void initSupplyChain() {
	try {
	    //Patient.init(config);
	    
	    //addFiller("   --- EE BRANCH ---");		      


	    UncountableResource fiber = new UncountableResource("fiber", 1);
	    Batch fiberBatch = Batch.mkPrototype(fiber, config);

	    
	    UncountableResource resin = new UncountableResource("resin", 1);
	    Batch resinBatch = Batch.mkPrototype(resin, config);

	    UncountableResource prepreg = new UncountableResource("prepreg", 1);
	    Batch prepregBatch = Batch.mkPrototype(prepreg, config);


	    UncountableResource aluminum = new UncountableResource("aluminum", 1);
	    Batch aluminumBatch = Batch.mkPrototype(aluminum, config);

	    CountableResource substrate[] = {
		new CountableResource("substrateSmall", 1),
		new CountableResource("substrateLarge", 1)};

	    Batch2 substrateBatch = Batch2.mkPrototype(substrate, config);

	    UncountableResource adhesive = new UncountableResource("adhesive", 1);
	    Batch adhesiveBatch = Batch.mkPrototype(adhesive, config);


	    CountableResource cell = new CountableResource("cell", 1);
	    Batch cellBatch = Batch.mkPrototype(cell, config);

	    CountableResource packagedCell = new CountableResource("packagedCell", 1);
	    Batch packagedCellBatch = Batch.mkPrototype(packagedCell, config);

	    CountableResource cellRM = new CountableResource("cellRM", 1);
	    Batch cellRMBatch = Batch.mkPrototype(cellRM, config);

	    CountableResource coverglass = new CountableResource("coverglass", 1);
	    Batch coverglassBatch = Batch.mkPrototype(coverglass, config);
	    

	    CountableResource coverglassAssembly = new CountableResource("coverglassAssembly", 1);
	    Batch coverglassAssemblyBatch = Batch.mkPrototype(coverglassAssembly, config);

	    
	    prepregProd = new Production(this, "prepregProd", config,
					 new Resource[] {fiberBatch, resinBatch},
					 prepregBatch);
	    add(prepregProd);

	    substrateProd = new Production(this, "substrateProd", config,
					 new Resource[] {prepregBatch, aluminumBatch},
					 substrateBatch);
	    add(substrateProd);

	    prepregProd.setQaReceiver(substrateProd.getEntrance(0), 1.0);	
	    

	    /*
	    eeRMSupplier = new Production(this, "eeRMSupplier", config,
					  new Resource[] {},
					  rmEEBatch);
	    add(eeRMSupplier);
				       
	    eeCmoProd = new Production(this, "eeCmoProd", config,
				       new Resource[] {rmEEBatch},
				       eeBatch);
	    
	    eeRMSupplier.setQaReceiver(eeCmoProd.getEntrance(0), 1.0);	
	    add(eeCmoProd);

	    eeCmoBackupProd = new Production(this, "eeCmoBackupProd", config,
				       new Resource[] {rmEEBatch},
				       eeBatch);
	    
	    eeCmoBackupProd.shareInputStore(eeCmoProd);
	    eeCmoBackupProd.sharePlan(eeCmoProd);
	    add(eeCmoBackupProd);

	    
	    
	    CountableResource pmEE = new CountableResource("PMEE", 1);
	    Batch pmEEBatch = Batch.mkPrototype(pmEE, config);

	    //----
	    eePMSupplier = new Production(this, "eePMSupplier", config,
					  new Resource[] {},
					  pmEEBatch);
	    add(eePMSupplier);
				       
	    

	    //---
	    eePackaging = new Production(this, "eePackaging", config,
					 new Resource[] {eeBatch, pmEEBatch},
					 eeBatch);
	    eePackaging.setNoPlan(); // driven by inputs
	    add(eePackaging);

	    eeCmoProd.setQaReceiver(eePackaging.getEntrance(0), 1.0);
	    eeCmoBackupProd.setQaReceiver(eePackaging.getEntrance(0), 1.0);	
	    eePMSupplier.setQaReceiver(eePackaging.getEntrance(1), 1.0);	

	    eeMedTech = new MedTech(this, "eeMedTech",
				    new Production[] {
					eeCmoProd,eeRMSupplier,eePMSupplier},
				    null);
	    
	    add(eeMedTech);

	    eeDC = new Pool(this, "eeDC", config,  eeBatch, new String[0]);
	    add(eeDC);

	    Delay d = eePackaging.mkOutputDelay(eeDC);
	    eePackaging.setQaReceiver(d, 1.0);	

	    eeDP = new Pool(this, "eeDP", config,  eeBatch, new String[0]);
	    add(eeDP);

	    eeHEP = new Pool(this, "eeHEP", config,  eeBatch, new String[0]);
	    add(eeHEP);

	    //---- DS production chain ----	    

	    addFiller("   --- DS BRANCH ---");		      


	    
	    dsRMSupplier = new Production(this, "dsRMSupplier", config,
					  new Resource[] {},
					  rmDSBatch);
	    add(dsRMSupplier);

	    BatchDivider dsRMBD = new BatchDivider(this, rmDSBatch,
						   dsRMSupplier.getPara().getDouble("outBatch"));

	    dsRMSupplier.setQaReceiver(dsRMBD, 1.0);

	    dsRMSplitter = new Splitter(this, rmDSBatch);
	    
	    

	    
	    dsRMBD.addReceiver(dsRMSplitter);
	    
	    dsCmoProd = new Production(this, "dsCmoProd", config,
				       new Resource[] {rmDSBatch},
				       dsBatch);
	    dsCmoProd.setNoPlan(); // driven by inputs (has no safety stock)

	    add(dsCmoProd);


	    dsCmoBackupProd = new Production(this, "dsCmoBackupProd", config,
					     new Resource[] {rmDSBatch},
					     dsBatch);
	    dsCmoBackupProd.shareInputStore(dsCmoProd);
	    dsCmoBackupProd.setNoPlan(); // driven by inputs (has no safety stock)
	    //dsCmoBackupProd.sharePlan(dsCmoProd);

	    add(dsCmoBackupProd);
	    
	    dsRMSplitter.addReceiver(dsCmoProd.mkInputDelay(0), 0.08);

	    dsProd = new Production(this, "dsProd", config,
				    new Resource[] {rmDSBatch},
				    dsBatch);

	    add(dsProd);
	    dsRMSplitter.addReceiver(dsProd.mkInputDelay(0), 0.90);
	    // DS CMO Prod has no QA stage of its own, and uses DS Prod's QA
	    dsCmoProd.setQaReceiver( dsProd.getQaEntrance(), 1.0);


	    CountableResource pmDS = new CountableResource("PMDS", 1);
	    Batch pmDSBatch = Batch.mkPrototype(pmDS, config);


	    //----
	    dsPMSupplier = new Production(this, "dsPMSupplier", config,
					  new Resource[] {},
					  pmDSBatch);
	    add(dsPMSupplier);
				       
	    dsPackaging = new Production(this, "dsPackaging", config,
					 new Resource[] {dsBatch, pmDSBatch},
					 dsBatch);
	    dsPackaging.setNoPlan(); // driven by inputs
	    add(dsPackaging);
	    
	    dsProd.setQaReceiver(dsPackaging.getEntrance(0), 1.0);	
	    dsPMSupplier.setQaReceiver(dsPackaging.getEntrance(1), 1.0);	

	    dsMedTech = new MedTech(this, "dsMedTech",
				    new Production[] {
					//dsCmoProd,
					dsProd,
					dsRMSupplier,dsPMSupplier},
				    new double[] {0.8, 1, 1}
				    );
	    
	    add(dsMedTech);
	    	   
	    dsDC = new Pool(this, "dsDC", config,  dsBatch, new String[0]);
	    add(dsDC);

	    
	    dsPackaging.setQaReceiver(dsPackaging.mkOutputDelay(dsDC), 1.0);	
	    
	    
	    dsDP = new Pool(this, "dsDP", config,  dsBatch, new String[0]);
	    add(dsDP);

	    dsHEP = new Pool(this, "dsHEP", config,  dsBatch, new String[0]);
	    add(dsHEP);

	    
	    //-- link the pools, based on the "from1" fields in its ParaSet
	    eeDC.setSuppliers(addedNodes);
	    eeDP.setSuppliers(addedNodes);
	    eeHEP.setSuppliers(addedNodes);

	    dsDC.setSuppliers(addedNodes);
	    dsDP.setSuppliers(addedNodes);
	    dsHEP.setSuppliers(addedNodes);

	    addFiller("   --- PATIENTS ---");		      
	    
	    wpq = new WaitingPatientQueue(this, config);
	    add(wpq);
	    
	    
	    spp = new ServicedPatientPool(this, config, wpq, eeHEP, dsHEP);
	    add(spp);
	    */
	    
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
	/*
	v.add(eeRMSupplier.report());
	v.add(eePMSupplier.report());
	v.add(eeCmoProd.report());
	v.add(eePackaging.report());
	v.add(eeDC.report());
	v.add(eeDP.report());
	v.add(eeHEP.report());
	v.add(eeMedTech.report());
	v.add(wpq.report());
	v.add(spp.report());
	*/
	for(Reporting r: reporters) v.add(r.report());

	//	v.add(dsRMSplitter.report());
	
	return String.join("\n", v);
    }

    public static class MakesDemo implements  MakesSimState {
   
	/** The Config object contains the parameters for
	    various supply chain elements, read from a
	    config file
	*/
	final private Config config0;
	final private Disruptions disruptions0;
	/** The data from the command line argument array, after the removal of options
	    interpreted by the constructor (such as -config XXX) will be put here. */
	public final String[] argvStripped;

	/** For use in RepeatTest */
	//	int repeat=1;
	

	/** Initializes the Config and Disruptions structures from their respective
	    config files. 
	    @param argv The actual command line array. The constructor will look for
	    the -config and -disrupt options in it.
	 */
	public MakesDemo(String[] argv) throws IOException, IllegalInputException    {

	    String confPath = "config/sc3.csv";
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
