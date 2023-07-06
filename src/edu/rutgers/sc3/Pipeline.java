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
public class Pipeline extends MultiStage {
    
    Vector<Middleman> stages = new Vector<>();

    Middleman firstStage() { return stages.get(0); }
    Middleman lastStage() { return stages.lastElement(); }
 
    
    public Pipeline(SimState state,Resource typical )        {
        super(state, typical);
    }

    
    void addStage(Middleman _nextStage) {
	if (!stages.isEmpty()) lastStage().addReceiver(_nextStage);
	stages.add(_nextStage);
    }

   
    public String hasBatches() {
	Vector<String> v = new Vector<>();
	int j=0;
	for(Middleman stage: stages) {
	    String s = "["+j+"]";
	    if (stage instanceof HasBatches) {
		s += ((HasBatches)stage).hasBatches();
	    } else {
		s += "?";
	    }
	    v.add(s);
	    j++;
	}
	return String.join(" : ", v);
    }

    /** Returns true if the production step is empty, and one
	should see if it needs to be reloaded */
    public boolean needsPriming() {
	return (firstStage() instanceof NeedsPriming) &&
	    ((NeedsPriming)firstStage()).needsPriming();
    }

    public String report() {
	double t = state.schedule.getTime();

	Vector<String> v = new Vector<>();
	int j=0;
	for(Middleman stage: stages) {
	    j++;
	    v.add("\n\tStage[" + j+ "] " +
		  ((stage instanceof Reporting)? ((Reporting)stage).report(): "???"));
	}
	

	return "["+getName()+": " + String.join("", v)+"]";
    }

    
}
