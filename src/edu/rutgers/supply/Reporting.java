package  edu.rutgers.supply;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

import   edu.rutgers.util.*;


/** An object implementing this interface will be able to tell something about its current state */
public interface Reporting {
    public String report();

    default String cname() {
	return Util.cname(this);
    }

    default String wrap(String msg) {
	String s = "[" + cname();

	if (this instanceof Named) {
	    s += "."+ ((Named)this).getName();
	}
	if (this instanceof Receiver) {
	    s +=  "("+((Receiver)this).getTypicalReceived().getName()+")";
	} else if (this instanceof Provider) {
	    s +=  "("+((Provider)this).getTypical().getName()+")";
	}
	s += ": " + msg +	    "]";
 
	return s;
  
    }

    static interface HasBatches {
	public String hasBatches();
    }
    
}
