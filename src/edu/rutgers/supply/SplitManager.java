package edu.rutgers.supply;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;


/** Uniform handling of split and no-split situations.

    <p>
    The SplitManager was created to handle the following
    problem. Sometimes, a production unit (e.g. an AbstractProduction)
    feeds to just a single receiever, which means that the receiver
    can be connected to the production unit's provider directly. In
    other cases, the production unit feeds to multiple receivers, with
    the output split between them according to fixed percentages; in
    this case, we connect the provider to the receivers via a
    Splitter. A SplitManager allows to handle these two cases in a
    uniform way, without introducing a superficial Splitter when it's
    not needed.

    <p>The caller uses real-valued arguments (in the range [0,1]) to indicate
    the percentage of output going to each destinations. The value of 1.0
    indicates that there is just one destination; for attaching multiple
    destinations, one uses a smaller number (the appropriate fraction)

    <p> Typically, it is the QA stage of a production unit that feeds
    into the SplitManager; but if the production unit has no QA stage,
    the SplitManager can be attached to whatever is the producton
    unit's last stage.
    
 */
public class SplitManager {

    /** The last stage of the producton unit that feeds into (or
        through) the SplitManager. The SplitManager will ensure that
	qaDelay will have exactly one receiver attached to it: either
	a "direct" "real" receiver, or a Splitter serving multiple
	"real" receivers.
    */
    Provider provider;
    /** The production unit itself */
    Named  parent;
    /** The resource being shipped out by the production unit */
    Resource outResource;
    String getName() { return parent.getName(); }

    /**
       @param _parent The production unit whose part this SplitManager is. It is provided as a parameter just so that its name can be printed
       @param _provider The last stage of the AbstractProduction node node that
       sends its output through this SplitManager. This is usually, but not
       always, the provider.
     */
    public SplitManager(Named  _parent, Resource _outResource, Provider _provider) {
	parent = _parent;
	provider =  _provider;
	outResource = _outResource;
    }

    /** This becomes non-null if, in fact, splitting to multiple destinations
	is needed */
    public Splitter outputSplitter = null;
    
    /** Connects the production unit's output directly to a single
	receiver, with no splitting. If this method is used, it should
	be used exactly once.
	@param rcv The receiver to which all output of the provider should
	go.
    */
    public void setQaReceiver(Receiver rcv) {
	setQaReceiver( rcv, 1.0);
    }

    /** Creates a path from the Provider (the last stage of a
	production unit) to a specified Receiver. This may be a direct
	connection (if fraction==1) or a connection via a Splitter (if
	fraction!=1, implying multiple receivers).

	<p>If this method is called several time (i.e. for several
	receivers), it is appropriate for the fraction values to sum
	to 1.0. But if they don't, it's OK; the Splitter will
	normalize the fractions by dividing them by their sum.

	@param rcv the Receiver to attach
	@param fraction the portion of the Provider's output (in the [0,1] range) that should go to that receiver
     */
    public void setQaReceiver(Receiver rcv, double fraction) {
	
	ArrayList<Receiver> has = provider.getReceivers();
	
	if (fraction > 1.0 || fraction < 0) throw new IllegalArgumentException("Illegal fraction=" + fraction);
	else if (fraction==0) {
	    return;
	} else if (fraction==1.0) {
	    if (has.size()!=0)  throw new IllegalArgumentException("Trying to add a receiver with fraction=" + fraction + " to node " + getName() + " that already has other receivers");
	    //System.out.println("DBEUG: SetQaReceiver: " + provider.getName() + " feeds to " + rcv.getName());

	    provider.addReceiver( rcv);
	} else {
	    if (has.size()>1) throw new IllegalArgumentException("Trying to add a receiver with fraction=" + fraction + " to node " + getName() + " that already has multiple ("+has.size()+") receivers");
	    else if (has.size()==0) {
		outputSplitter = new Splitter( provider.getState(), (Batch)outResource);
		provider.addReceiver( outputSplitter);
	    } else {
		if (has.get(0) != outputSplitter || outputSplitter==null) throw new IllegalArgumentException("Trying to add a receiver with fraction=" + fraction + " to node " + getName() + " whose already-set receiver is not a splitter");
	    }
	    outputSplitter.addReceiver(rcv, fraction);
	}
    }
    

}


