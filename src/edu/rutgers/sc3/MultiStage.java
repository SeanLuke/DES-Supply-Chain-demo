package  edu.rutgers.sc3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;
import edu.rutgers.sc3.Production.NeedsPriming;
import edu.rutgers.supply.Reporting.HasBatches;

/** The base for two classes ThrottledStage and Pipeline) which both
    consist of a series of stages throw which product batches go.
*/
abstract class MultiStage extends Middleman implements NeedsPriming, HasBatches, Reporting {

    abstract Middleman firstStage();
    abstract Middleman lastStage();
    
    public MultiStage(SimState state,Resource typical)        {
        super(state, typical);
    }

       
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	return firstStage().accept(provider, resource, atLeast, atMost);
    }

    /** This can only be used ater the pipeline has been fully assembled */
    public boolean addReceiver(Receiver receiver) {
	System.out.println( lastStage().getName() + " sends to "+ receiver.getName());
	
	return lastStage().addReceiver(receiver);
    }

    //    private double everReleased = 0;
    //    private double ot0 = -2;

    public boolean offerReceiver(Receiver receiver, double atMost) {
	throw new UnsupportedOperationException();
	/*
	boolean z = lastStage().offerReceiver(receiver, atMost);

	double ot = getLastOfferTime();
	if (z && ot > ot0) {
	    everReleased += Batch.getContentAmount(  getLastAcceptedOffers());
	    ot0 = ot;
	    }
	    return z;
	*/
    }
 

    public double getLastOfferTime() { return lastStage().getLastOfferTime(); }


    public ArrayList<Resource> getLastAcceptedOffers() {
	return lastStage().getLastAcceptedOffers();
    }

    
    public void setFaultRateIncrease(double x, Double _untilWhen) {
	((NeedsPriming)firstStage()).setFaultRateIncrease(x, _untilWhen);
    }
 
    public double getEverReleased() {
	if (lastStage() instanceof HasBatches) {
	    return ((HasBatches)lastStage()).getEverReleased();
	} else {
	    throw new UnsupportedOperationException();
	}
    }
     
}
