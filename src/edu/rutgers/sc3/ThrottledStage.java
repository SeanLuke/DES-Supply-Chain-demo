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

/** A ThrottleQueue + ProdDelay combo, packaged into a Middleman. This
    can be an entire production stage, or a stage of the production
    pipeline.
*/
public class ThrottledStage extends MultiStage  {
    final ThrottleQueue needProd;
    final ProdDelay prod;

    Middleman firstStage() { return needProd; }
    Middleman lastStage() { return prod; }

    public ThrottledStage(SimState state, ThrottleQueue _needProd,
			 ProdDelay _prod //, Resource typical
			 )        {
        super(state, _prod.getTypicalProvided());
	needProd = _needProd;
	prod = _prod;
    }

    
    /** Returns true if the production step is empty, and one
	should see if it needs to be reloaded */
    public boolean needsPriming() {
	return needProd!=null && 
	    needProd.getAvailable()==0 && prod.getSize()==0;
    }
    
    public void setFaultRateIncrease(double x, Double _untilWhen) {
	prod.setFaultRateIncrease(x, _untilWhen);
    }

    /** How many batches are there waiting for processing, and
	how many are waiting for processing */
    public String hasBatches() {
	return needProd.hasBatches();
    }

   public String report() {
       return "Queued(+prod): "+hasBatches()+
	   "; " +prod.report();
    }
    
}
