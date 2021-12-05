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


	    AbstractDistribution  faultyPortionDistribution = new Uniform(0.05, j==0? 0.15: 0.30, random);


	    preprocStore[j] = new PreprocStorage(this, "PrePStore" + j, ingStore[j], 1000,  qaDelayDistribution, faultyPortionDistribution);
	    add(preprocStore[j]);
	}
	packmatStore = new IngredientStorage(this,"PackMatStore", config, packmat);
	add(packmatStore);

	AbstractDistribution  faultyPortionDistribution = new Uniform(0.05, 0.15, random);
	
	testedPackmatStore =  new PreprocStorage(this, "TestedPackmatStore", packmatStore, 1000,  qaDelayDistribution, faultyPortionDistribution);
	add( testedPackmatStore);
	
	CountableResource product = new CountableResource("Product",0);

	postprocStore = new  sim.des.Queue(this, product);
	postprocStore.setName("PostPStore");
	postprocStore.setCapacity(1000);
	
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
				  
	
	dispatchStore = new  DispatchStorage(this, "Dispatch",
					     packaged, 500,
					     new Uniform(5,10, random));
	
	dispatchStore.setCapacity(500);
	
	packaging.setDispatchStore(dispatchStore);

	add(packaging);
	add(dispatchStore);	
	System.out.println("===== Start: =========\n" + report());
	} catch(IllegalInputException ex) {
	    System.out.println("Unable to create a model due to a problem with the configuration parameters:\n" + ex);
	    System.exit(1);
	}
    }

    public void	finish() {
	System.out.println("===== Finish: =========\n" + report());
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
    
    static  String[] argv;
    static Config config;
    
    public static void main(String[] _argv) throws IOException, IllegalInputException {
	argv= _argv;

	File f= new File("config/pharma.csv");
	config  = Config.readConfig(f);
	
	
	doLoop(Demo.class, argv);
	
	System.exit(0);
    }

    
}
