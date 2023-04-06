package  edu.rutgers.supply;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

import edu.rutgers.util.Util;
import edu.rutgers.util.IllegalInputException;


/** An object implementing this interface will be able to tell
    something about its current state, and its history (how much
    work it has done, how good that work has been, etc).
*/
public interface Reporting {
    public String report();

    /** Returns the last component of the class name of this object.
	E.g. for an edu.rutgers.pharma3.QaDelay it will return "QaDelay".
     */
    default String cname() {
	return Util.cname(this);
    }

    /** Given a message, prepends it with a text informing what object it
	has come from (e.g. "QaDelay.Drug: ").
     */
    default String wrap(String msg) {
	String s = "[" + cname();

	if (this instanceof Named) {
	    s += "."+ ((Named)this).getName();
	}
	if (this instanceof Receiver) {
	    s +=  "("+((Receiver)this).getTypicalReceived().getName()+")";
	} else if (this instanceof Provider) {
	    s +=  "("+((Provider)this).getTypicalProvided().getName()+")";
	}
	s += ": " + msg +	    "]";
 
	return s;
  
    }

    /** A convenience interface serving to indicate that a class has the
	reporting method HasBatches.hasBatches() */
    static interface HasBatches {
	/** Reports the amount of stuff stored
	    in (e.g.) a Queue or Delay both in term of units and
	    in terms of batches */
	public String hasBatches();
    }
    
}
