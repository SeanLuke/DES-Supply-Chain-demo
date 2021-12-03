package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

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

	CountableResource[] ing = {new CountableResource("Ing0",0), new CountableResource("Ing1",0)};
	final int NI = ing.length;
	CountableResource packmat = new CountableResource("Pack.Mat.",0);

	ingStore = new IngredientStorage[NI];
	preprocStore = new PreprocStorage[NI];
	AbstractDistribution  qaDelayDistribution = new Uniform(1,4,random);	for(int j=0; j<NI; j++) {
	    ingStore[j] = new IngredientStorage(this, "StoreOfIng" + j,  ing[j], 1000);
	    add(ingStore[j]);


	    AbstractDistribution  faultyPortionDistribution = new Uniform(0.05, j==0? 0.15: 0.30, random);


	    preprocStore[j] = new PreprocStorage(this, "PrePStore" + j, ingStore[j], 1000,  qaDelayDistribution, faultyPortionDistribution);
	    add(preprocStore[j]);
	}
	packmatStore = new IngredientStorage(this,"Pack.Mat.Store",  packmat, 1000);
	add(packmatStore);

	AbstractDistribution  faultyPortionDistribution = new Uniform(0.05, 0.15, random);
	
	testedPackmatStore =  new PreprocStorage(this, "TestedPackmatStore", packmatStore, 1000,  qaDelayDistribution, faultyPortionDistribution);
	add( testedPackmatStore);
	
	CountableResource product = new CountableResource("Product",0);

	postprocStore = new  sim.des.Queue(this, product);
	postprocStore.setName("PostPStore");
	postprocStore.setCapacity(1000);
	
	production = new Production(this, "Prod",
				    preprocStore,
				    postprocStore,
				    new int[] {10,10}, 10,
				    product, 1000,
				    //prodDelayDistribution,
				    new Uniform(1,3, random),
				    // qaDelayDistribution,
				    new Uniform(1,4, random),
				    // faultyPortionDistribution
				    new Uniform(0.1, 0.2, random));
	add(production);

	CountableResource packaged = new CountableResource("PackagedProduct",0);
	packaging = new Packaging(this, "Packaging",
				  postprocStore,
				  testedPackmatStore,
				  10,
				  packaged,
				  1000,
				  //prodDelayDistribution,
				  new Uniform(1,3, random),
				  //qaDelayDistribution,
				  new Uniform(1,4, random),
				  //faultyPortionDistribution
				  new Uniform(0.1, 0.2, random));
				  
	
	dispatchStore = new  DispatchStorage(this, "Dispatch",
					     packaged, 500,
					     new Uniform(5,10, random));
	
	dispatchStore.setCapacity(500);
	
	packaging.setDispatchStore(dispatchStore);

	add(packaging);
	add(dispatchStore);

	
	System.out.println("===== Start: =========\n" + report());
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
    
    public static void main(String[] _argv){
	argv= _argv;

	doLoop(Demo.class, argv);
	
	System.exit(0);
    }

    
}
