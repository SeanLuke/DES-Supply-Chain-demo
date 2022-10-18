package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
//import sim.des.portrayal.*;

import edu.rutgers.util.*;

class SplitManager //implements Named
{

    /** Production and MaterialSupplier implement this */
    interface HasQA {
	void setQaReceiver(Receiver rcv, double fraction);
	QaDelay getQaDelay();
	/** Returns the last existing stage of this production unit. Typically
	    this is the qaDelay, but some units (CMO Track A) don't have QA. */
	Provider getTheLastStage();
	
	/** Tries to make a batch, if resources are available
	    @return true if a batch was made; false if not enough input resources
	    was there to make one, or the current plan does not call for one

	*/
	boolean mkBatch(SimState state);


    }

    
    Provider qaDelay;
    Steppable parent;
    Resource outResource;
    String getName() { return((Named) parent).getName(); }

    /**
       @param The last stage of the Productin of MaterialSupplier node that
       sends its output through this SplitManager. This is usually, but not
       always, the qaDelay.
     */
    SplitManager(Steppable _parent, Resource _outResource, Provider _qaDelay) {
	parent = _parent;
	qaDelay =  _qaDelay;
	outResource = _outResource;
	//setName(((Named)qaDelay).getName());
    }
    
    Splitter outputSplitter = null;
    
    /** Sets the destination for the product that has passed the QA. This
	should be called after the constructor has returned, and before
	the simulation starts.
	@param rcv The place to which good stuff goes after QA
    */
    void setQaReceiver(Receiver rcv) {
	setQaReceiver( rcv, 1.0);
    }

    /** Creates a path from the last stage of the production unit to a
	specified Receiver. This may be a direct connection (if fraction==1)
	or a connection via a Splitter (if fraction!=1, implying multiple
	receivers).
     */
    void setQaReceiver(Receiver rcv, double fraction) {
	
	ArrayList<Receiver> has = qaDelay.getReceivers();
	
	if (fraction > 1.0 || fraction < 0) throw new IllegalArgumentException("Illegal fraction=" + fraction);
	else if (fraction==0) {
	    return;
	} else if (fraction==1.0) {
	    if (has.size()!=0)  throw new IllegalArgumentException("Trying to add a receiver with fraction=" + fraction + " to node " + getName() + " that already has other receivers");
	    qaDelay.addReceiver( rcv);
	} else {
	    if (has.size()>1) throw new IllegalArgumentException("Trying to add a receiver with fraction=" + fraction + " to node " + getName() + " that already has multiple ("+has.size()+") receivers");
	    else if (has.size()==0) {
		outputSplitter = new Splitter( qaDelay.getState(), (Batch)outResource);
		qaDelay.addReceiver( outputSplitter);
	    } else {
		if (has.get(0) != outputSplitter || outputSplitter==null) throw new IllegalArgumentException("Trying to add a receiver with fraction=" + fraction + " to node " + getName() + " whose already-set receiver is not a splitter");
	    }
	    outputSplitter.addReceiver(rcv, fraction);
	}
    }
    

}


