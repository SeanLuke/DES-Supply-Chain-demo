package  edu.rutgers.sc3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;
import edu.rutgers.sc3.Production.NeedsPriming;

/** A ThrottleQueue + ProdDelay combo, packaged into a Middleman. This can be an entire production
    stage, or a stage of the production pipeline.
*/
public class ThrottledStage extends Middleman implements NeedsPriming, Reporting.HasBatches {
    final ThrottleQueue needProd;
    final ProdDelay prod;

    public ThrottledStage(SimState state, ThrottleQueue _needProd,
			 ProdDelay _prod //, Resource typical
			 )        {
        super(state, _prod.getTypicalProvided());
	needProd = _needProd;
	prod = _prod;
    }

    
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	return needProd.accept(provider, resource, atLeast, atMost);
    }

    public boolean addReceiver(Receiver receiver) {
	return prod.addReceiver(receiver);
    }

    public boolean offerReceiver(Receiver receiver, double atMost) {
	return prod.offerReceiver(receiver, atMost);
    }
 
    public ArrayList<Resource> getLastAcceptedOffers() {
	return prod.getLastAcceptedOffers();
    }


    /** Returns true if the production step is empty, and one
	should see if it needs to be reloaded */
    boolean needsPriming() {
	return needProd!=null && 
	    needProd.getAvailable()==0 && prodDelay.getSize()==0;
    }

    void setFaultRateIncrease(double x, Double _untilWhen) {
	prodDelay.setFaultRateIncrease(x, _untilWhen);
    }

    /** How many batches are there waiting for processing, and
	how many are waiting for processing */
    public String hasBatches() {
	return throttleQueue.hasBatches();
    }

}
