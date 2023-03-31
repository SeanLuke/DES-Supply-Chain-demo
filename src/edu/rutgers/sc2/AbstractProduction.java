package  edu.rutgers.sc2;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

import edu.rutgers.util.*;


/** Includes certain common features of production
    units. Production and MaterialSupplier are the two classes that
    extends this class. */
public abstract class AbstractProduction extends sim.des.Macro {

    /** Models the delay taken by the QA testing at the output. This
	may be null if this production facility has no QA stage (such
	as CMO Track A).
    */
    protected QaDelay qaDelay;
    public QaDelay getQaDelay() { return qaDelay; }


    /** Returns the last existing stage of this production
	unit. Typically this is the qaDelay, but Production overrides
	it, because some Production units (CMO Track A) don't have QA. */
    public Provider getTheLastStage() { return qaDelay;    }
    
    
    /** Tries to make a batch, if resources are available
	@return true if a batch was made; false if not enough input resources
	was there to make one, or the current plan does not call for one
	
    */
    abstract boolean mkBatch();

    //--------- Managing the downstream operations
    /** The constructor of each derived class must initialize this */
    SplitManager sm;

    /** Adds a destination to the output of this production unit
	(typically, the QA delay). This should be called after the
	constructor has returned, and before the simulation starts.

	@param rcv The place to which (some portion of) output goes
	@param fraction This should be 1.0 if all output goes to the
	same place, or the appropriate fraction (a number in the [0;1]
	range) for each destination if there are multiple destinations.
    */
    public void setQaReceiver(Receiver rcv, double fraction) {  
	sm.setQaReceiver(rcv, fraction);
    }

    //-------- Stats -----------------
    /** Stats for planning */
    double[] computeABG() {
	return qaDelay!=null? qaDelay.computeABG() : new double[] {0,0,1};
    }
  
    double computeGamma() {
	return computeABG()[2];
    }

  

}
