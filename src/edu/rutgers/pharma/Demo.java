package  edu.rutgers.pharma;

import java.io.*;
import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

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

    IngredientStorage[] ingStore;
    IngredientStorage packmatStore;
    PreprocStorage[] preprocStore;
    PreprocStorage testedPackmatStore;
    Production production;
    Packaging packaging;
    sim.des.Queue postprocStore;
    DispatchStorage dispatchStore;

    /** Creates a named queue for a specified resource */
    private sim.des.Queue mkQueue(String name, CountableResource resource)  throws IllegalInputException {	
	sim.des.Queue q  = new  sim.des.Queue(this, resource);
	q.setName(name);
	ParaSet para = config.get2(name);
	q.setCapacity(para.getDouble("capacity"));
	return q;
    }
	
    
    /** Here, the supply network elements are added to the Demo object */
    public void start(){
	super.start();
	try {
	CountableResource[] ing = {new CountableResource("Ing0",0), new CountableResource("Ing1",0)};
	final int NI = ing.length;
	CountableResource packmat = new CountableResource("Pack.Mat.",0);

	ingStore = new IngredientStorage[NI];
	preprocStore = new PreprocStorage[NI];
	AbstractDistribution  qaDelayDistribution = new Uniform(1,4,random);
	for(int j=0; j<NI; j++) {
	    ingStore[j] = new IngredientStorage(this, "IngStore" + j, config,  ing[j]);
	    add(ingStore[j]);
	    preprocStore[j] = new PreprocStorage(this, "PreprocStore" + j, config, ingStore[j]);
	    add(preprocStore[j]);
	}
	packmatStore = new IngredientStorage(this,"PackMatStore", config, packmat);
	add(packmatStore);

	AbstractDistribution  faultyPortionDistribution = new Uniform(0.05, 0.15, random);
	
	testedPackmatStore =  new PreprocStorage(this, "TestedPackMatStore", config, packmatStore);
	add( testedPackmatStore);
	
	CountableResource product = new CountableResource("Product",0);

	postprocStore = mkQueue( "PostprocStore", product);
	
	production = new Production(this, "Production", config,
				    preprocStore,
				    postprocStore,
				    product);
	add(production);

	CountableResource packaged = new CountableResource("PackagedProduct",0);
	packaging = new Packaging(this, "Packaging", config,
				  postprocStore,
				  testedPackmatStore,
				  packaged);
				  	
	dispatchStore = new  DispatchStorage(this, "DispatchStore", config,
					     packaged);	
	
	packaging.setDispatchStore(dispatchStore);

	add(packaging);
	add(dispatchStore);
	doReport("Start");
	} catch(IllegalInputException ex) {
	    System.out.println("Unable to create a model due to a problem with the configuration parameters:\n" + ex);
	    System.exit(1);
	}
	final int CENSUS_INTERVAL=500;
	schedule.scheduleRepeating(new Reporter(), CENSUS_INTERVAL);

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
	for(IngredientStorage q: ingStore) v.add(q.report());
	v.add(packmatStore.report());
	for(PreprocStorage q: preprocStore) v.add(q.report());
	v.add(testedPackmatStore.report());
	v.add(production.report());
	v.add("[PostProcStore has " + postprocStore.getAvailable() + " units]");
	v.add(packaging.report());
	v.add( dispatchStore.report());
	return String.join("\n", v);
    }
    
    static Config config;

    /** Extracts a few command-line options we understand, and leaves
	the rest of them to MASON.
    */
    public static void main(String[] argv) throws IOException, IllegalInputException {
	String confPath = "config/pharma.csv";

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
