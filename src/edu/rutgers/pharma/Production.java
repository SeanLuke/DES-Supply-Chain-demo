package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

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
    final double[] inBatchSizes;
    /** How big is the output batch? */
    final double outBatchSize;

    /** Where inputs come from */
    final PreprocStorage[] preprocStore;
    final sim.des.Queue postprocStore;

    /** Dummy receivers used for the consumption of ingredients */
    final Sink[] sink;

    Production(SimState state, String name, Config config,
	       PreprocStorage[] _preprocStore,
	       sim.des.Queue _postprocStore,
	       Resource outResource ) throws IllegalInputException
    {
	super(state, outResource);
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	setCapacity(para.getDouble("capacity"));

	preprocStore = _preprocStore;
	postprocStore =	_postprocStore;

	//	inBatchSizes = _inBatchSizes;
	inBatchSizes = para.getDoubles("inBatch");
	if (inBatchSizes.length!=preprocStore.length) throw new  IllegalInputException("Mismatch of the number of inputs: given " + preprocStore.length + " preproc stores, but " + inBatchSizes.length + " input batch sizes");
	//outBatchSize = _outBatchSize;
	outBatchSize = para.getDouble("batch");
			   
	qaDelay = new QaDelay(state,resource, para.getDistribution("faulty",state.random));
	qaDelay.setDelayDistribution(para.getDistribution("qaDelay",state.random));
	qaDelay.addReceiver(postprocStore);
	qaDelay.addReceiver(this);
	qaDelay.setOfferPolicy​(Provider.OFFER_POLICY_FORWARD);
	
	prodDelay = new Delay(state,resource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",state.random));
				       
	prodDelay.addReceiver(qaDelay);

	sink = new Sink[preprocStore.length];
	for(int j=0; j<sink.length; j++) {
	    sink[j] = new MSink(state,preprocStore[j].getTypicalProvided());
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
	double haveNow = getAvailable() + prodDelay.getDelayed() +
	    qaDelay.getDelayed();
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
	    Resource onTheTruck = new CountableResource((CountableResource)getTypicalProvided(), outBatchSize);
	    Provider provider = null;  // why do we need it?
	    prodDelay.accept(provider, onTheTruck, outBatchSize, outBatchSize);
	    batchesStarted++;
	    everStarted += outBatchSize;
	}
	
	//  the Queue.step() call resource offers to registered receivers
	super.step(state);
    }

    public String report() {
	return "[Production."+getName()+
	    ", has "+ prodDelay.getDelayed()+
	    "+"+prodDelay.getAvailable()+
	    " in the work, " +  qaDelay.getDelayed() +
	    "+"+qaDelay.getAvailable()+
	    " in QA, and " + getAvailable() + " in ready storage. Ever started: "+everStarted+". Produced: faulty="+qaDelay.badResource+", good=" + qaDelay.releasedGoodResource+"]";

    }


}
