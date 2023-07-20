package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** A Packaging facility reprocessing Storage unit receievs an ingredient from
    an Ingredient Storage unit (via a built-in quality check unit),
    and supplies quality-assured ingredient to the Production unit.


  */
class Packaging extends sim.des.Queue implements Reporting {

    Delay prodDelay;
    /** Models the delay taking by the QA testing at the input
	*/
    QaDelay qaDelay;

    Sink sinkProduct, sinkPackaging;

    sim.des.Queue postprocStore;
    PreprocStorage testedPackmatStore;
    sim.des.Queue dispatchStore;
    //Source packagingMachine;
    double batchSize;
    
    Packaging(SimState _state, String name, Config config,
	      sim.des.Queue _postprocStore,
	      PreprocStorage _testedPackmatStore,
	      Resource outResource) throws IllegalInputException
    {
	super(_state, outResource);
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	setCapacity(para.getDouble("capacity"));

	postprocStore = _postprocStore ;
	testedPackmatStore = _testedPackmatStore;
	batchSize = para.getDouble("batch");
	
	qaDelay = new QaDelay(getState(),resource,  para.getDistribution("faulty",getState().random));
	qaDelay.setDelayDistribution(para.getDistribution("qaDelay",getState().random));
	qaDelay.setOfferPolicy​(Provider.OFFER_POLICY_FORWARD);
	
	prodDelay = new Delay(getState(),resource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",getState().random));
	prodDelay.addReceiver(qaDelay);

	
	sinkProduct = new Sink(getState(),  _postprocStore.getTypicalProvided());
	sinkPackaging = new Sink(getState(), _testedPackmatStore.getTypicalProvided());

	addReceiver(dispatchStore);
    }

    /** This method should be called immediately after the constructor
	cteates a Packaging method. This code is put into a separate
	method so that we can create a DispatchStore object after creating
	a Packaging object
     */
    void setDispatchStore( sim.des.Queue _dispatchStore) {
	dispatchStore =  _dispatchStore;
	qaDelay.addReceiver(dispatchStore );
	qaDelay.addReceiver(this);
    }



    /** Do we have enough input materials of each kind to make a batch? */
    private boolean hasEnoughInputs() {
	return  testedPackmatStore.getAvailable()>=batchSize &&
	    postprocStore.getAvailable()>=batchSize;
    }

    int batchesStarted=0;

    
    public void step​(sim.engine.SimState _state) {
	double workedUpon = prodDelay.getDelayed() +  qaDelay.getDelayed() + batchSize;
	boolean haveSpace = (getAvailable() + workedUpon  <=getCapacity()) ||
	    (dispatchStore.getAvailable() +  workedUpon  <= dispatchStore.getCapacity());
	
	if (haveSpace && hasEnoughInputs()) {
	    postprocStore.provide( sinkProduct, batchSize);
	    testedPackmatStore.provide( sinkPackaging, batchSize);
	    
	    //	    System.out.println("At t=" + getState().schedule.getTime() + ", Packaging starts on a batch");
	    Resource batch = new CountableResource((CountableResource)getTypicalProvided(), batchSize);
	    Provider provider = null;  // why do we need it?
	    prodDelay.accept(provider, batch, batchSize, batchSize);
	    batchesStarted++;
	}
	
	//  the Queue.step() call resource offers to registered receivers
	super.step(getState());
    }

    public String report() {
	return "[Packaging."+getName()+", has "+ prodDelay.getDelayed() + " in the work, " +  qaDelay.getDelayed() + " in QA, " + getAvailable() + " in ready storage. Packaged: faulty="+qaDelay.badResource+", good=" + qaDelay.releasedGoodResource+"]";

    }

}
