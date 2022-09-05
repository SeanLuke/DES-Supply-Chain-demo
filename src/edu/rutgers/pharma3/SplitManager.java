package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;

class SplitManager //implements Named
{

    /** Production and MaterialSupplier implement this */
    interface HasQA {
	void setQaReceiver(Receiver rcv, double fraction);
	QaDelay getQaDelay();

    }

    
    Provider qaDelay;
    Steppable parent;
    Resource outResource;
    String getName() { return((Named) parent).getName(); }
    
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


