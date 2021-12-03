package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

/** A Production plant takes ingredients from 2 Preprocessing storage units,
    puts them through production and QA delays, and sends the output 
    to Postprocessing store.
  */
class Production extends sim.des.Queue
    implements Reporting {
    Delay prodDelay;
    /** Models the delay taking by the QA testing at the input
	*/
    QaDelay qaDelay;

    /** How many units of each input need to be taken to start cooking a batch? */
    final int[] inBatchSizes;
    /** How big is the output batch? */
    final int outBatchSize;

    /** Where inputs come from */
    final PreprocStorage[] preprocStore;
    final sim.des.Queue postprocStore;

    /** Dummy receivers used for the consumption of ingredients */
    final Sink[] sink;

    Production(SimState state, String name,
	       PreprocStorage[] _preprocStore,
	       sim.des.Queue _postprocStore,
	       int[] _inBatchSizes, int _outBatchSize,
	       Resource outResource,
	       int maximum,		   
		   AbstractDistribution  prodDelayDistribution,
		   AbstractDistribution  qaDelayDistribution,
		   AbstractDistribution  faultyPortionDistribution
		   )
    {
	super(state, outResource);
	setCapacity(maximum);
	setName(name);

	inBatchSizes = _inBatchSizes;
	outBatchSize = _outBatchSize;
	preprocStore = _preprocStore;
	postprocStore =	_postprocStore;
			   
	qaDelay = new QaDelay(state,resource, faultyPortionDistribution);
	qaDelay.setDelayDistribution(qaDelayDistribution);
	qaDelay.addReceiver(postprocStore);
	qaDelay.addReceiver(this);
	qaDelay.setOfferPolicy​(Provider.OFFER_POLICY_FORWARD);
	
	prodDelay = new Delay(state,resource);
	prodDelay.setDelayDistribution(prodDelayDistribution);
	prodDelay.addReceiver(qaDelay);

	sink = new Sink[preprocStore.length];
	for(int j=0; j<sink.length; j++) {
	    sink[j] = new MSink(state,preprocStore[j].getTypical());
	}
  
    }


    /** Do we have enough input materials of each kind to make a batch? */
    private boolean hasEnoughInputs() {
	for(int j=0; j<inBatchSizes.length; j++) {
	    if (preprocStore[j].getAvailable()<inBatchSizes[j]) return false;
	}
	return true;
    }

    int batchesStarted=0;
    double everStarted = 0;
    
    public void step​(SimState state) {
	double haveNow = getAvailable() + prodDelay.getTotal() +
	    qaDelay.getTotal();
	if (haveNow  + outBatchSize < getCapacity() &&	    
	    hasEnoughInputs()) {

	    for(int j=0; j<inBatchSizes.length; j++) {
		//preprocStore[j].getResource().decrease(inBatchSizes[j]);
		double s0 =	preprocStore[j].getAvailable();
		preprocStore[j].provide(sink[j], inBatchSizes[j]);
		double s1 =	preprocStore[j].getAvailable();
		//		System.out.println("At t=" + state.schedule.getTime() + ", Production took " + inBatchSizes[j] + " from " + preprocStore[j].getName() + "; changed from " +s0 + " to " + s1);
	    }
	    
	    //	    System.out.println("At t=" + state.schedule.getTime() + ", Production starts on a batch; pp0 still has "+ preprocStore[0].getAvailable()+", pp1 still has "+ preprocStore[1].getAvailable() );
	    Resource onTheTruck = new CountableResource((CountableResource)getTypical(), outBatchSize);
	    Provider provider = null;  // why do we need it?
	    prodDelay.accept(provider, onTheTruck, outBatchSize, outBatchSize);
	    batchesStarted++;
	    everStarted += outBatchSize;
	}
	
	//  the Queue.step() call resource offers to registered receivers
	super.step(state);
    }

    public String report() {
	return "[Production."+getName()+", has "+ prodDelay.getTotal()+"+"+prodDelay.getAvailable()+ " in the work, " +  qaDelay.getTotal() +"+"+qaDelay.getAvailable()+ " in QA, and " + getAvailable() + " in ready storage. Ever started: "+everStarted+". Produced: faulty="+qaDelay.badResource+", good=" + qaDelay.releasedGoodResource+"]";

    }


}
