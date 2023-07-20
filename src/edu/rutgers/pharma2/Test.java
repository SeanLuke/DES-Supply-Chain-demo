package  edu.rutgers.pharma2;

import java.io.*;
import java.util.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import ec.util.MersenneTwisterFast;

/** The main class for a  simple test of Delay */
public class Test extends SimState {

    static boolean verbose=true;
      
    public Test(long seed)    {
	super(seed);
    }
     

    /** Here, the supply network elements are added to the Demo object */
    public void start(){
	super.start();
	Prod prod = new Prod(this);
	schedule.scheduleRepeating(prod);    
    }
      
    public static void main(String[] argv) throws IOException {
	
	Vector<String> va = new Vector<String>();
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    if (a.equals("-verbose")) {
		verbose = true;
	    } else {
		va.add(a);
	    }
	}

	argv = va.toArray(new String[0]);
		
	doLoop(Test.class, argv);
	
	System.exit(0);
    }

    /** This steppable has a Delay element in it, into which it puts 100 units 
	every day... */
    static class Prod implements    Steppable {

	final Delay prodDelay;
	
	final double[] inBatchSizes = {100};
	/** How big is the output batch? */
	final double outBatchSize = 100;

	Source source;
	
	Prod(SimState _state)     {
	    
	    Resource outResource = new CountableResource("Pills", 0);
	
	    prodDelay = new Delay(_state,outResource);
	    AbstractDistribution dist = new Uniform(10,12, _state.random);	
	    prodDelay.setDelayDistribution( dist);
	    
	    Sink sink = new Sink(_state,outResource);
	    prodDelay.addReceiver(sink);
	    source = new Source(_state, outResource);
	}

	int batchesStarted=0;
	double everStarted = 0;
	
	public void step​(SimState _state) {
	    
	    batchesStarted++;
	    everStarted += outBatchSize;
	    
	    //if (verbose) System.out.println("At t=" + _state.schedule.getTime() + ", Production about to start on batch no. "+batchesStarted+"; in the works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());
	    Resource onTheTruck = new CountableResource((CountableResource)prodDelay.getTypicalProvided(), outBatchSize);
	    Provider provider = source; //null;  // why do we need it?
	    prodDelay.accept(provider, onTheTruck, outBatchSize, outBatchSize);
	    if (verbose) System.out.println("At t=" + _state.schedule.getTime() + ", Production has started  on batc no. "+batchesStarted+"; in the works=" +	    prodDelay.getDelayed()+"+"+prodDelay.getAvailable());
	}
	
    }
}
