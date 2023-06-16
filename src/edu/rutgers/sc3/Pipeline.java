package  edu.rutgers.sc3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;
//import edu.rutgers.supply.Disruptions.Disruption;
import edu.rutgers.sc3.Production.NeedsPriming;
import edu.rutgers.supply.Reporting.HasBatches;

/** A series of stages throw which product batches go. Each stage
    is normally capable of accepting batches at any time,
    because it's either concurrent-processing, or has an input
    buffer in front of the one-batch-at-a-time stage
*/
public class Pipeline extends Middleman implements NeedsPriming, HasBatches {
    
    Vector<Middleman> stages = new Vector<>();

    public Pipeline(SimState state,Resource typical
			 )        {
        super(state, typical);
    }

    
    void addStage(Middleman _nextStage) {
	stages.add(_nextStage);
    }

   
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	return stages.get(0).accept(provider, resource, atLeast, atMost);
    }

    /** This can only be used ater the pipeline has been fully assembled */
    public boolean addReceiver(Receiver receiver) {
	return stages.lastElement().addReceiver(receiver);
    }

    public boolean offerReceiver(Receiver receiver, double atMost) {
	return stages.lastElement().offerReceiver(receiver, atMost);
    }

    public ArrayList<Resource> getLastAcceptedOffers() {
	return stages.lastElement().getLastAcceptedOffers();
    }

    
    /** Returns true if the production step is empty, and one
	should see if it needs to be reloaded */
    boolean needsPriming() {
	return (stages.get(0) instanceof NeedsPriming) &&
	    ((NeedsPriming)stages.get(0)).needsPriming();
    }

    void setFaultRateIncrease(double x, Double _untilWhen) {
	((NeedsPriming)stages.get(0)).setFaultRateIncrease(x, _untilWhen);
    }

    public String hasBatches() {
	Vector<String> v = new Vector<>();
	int j=0;
	for(Middleman stage: stages) {
	    String s = "["+j+"]";
	    s += ((HasBatches)stage.hasBatches());
	    v.add(s);
	    j++;
	}
	return String.join(" : ", v);

    }

    
    
}
