package  edu.rutgers.sc2;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

class MedTech implements Named, BatchProvider, Reporting {

    Production prod;

    /**
       @param _prod To whom orders will be sent
     */
    MedTech(String _name, Production _prod) {
	prod = _prod;
	setName(_name);
    }

    double everReceivedOrders = 0;
    
    
    /** Despite the name, it does not actually feed anything to the receiver,
	but requests the producer to produce some products which will eventually
	propagate to the receiver */       
    public double feedTo(Receiver r, double amt) {
	prod.addToPlan(amt);
	// FIXME: should also order some raw materials for the production node,
	// so that it won't have to rely on safety stocks
	everReceivedOrders += amt;
	return amt;
    }

    /** Nothing is done here. This class implements Steppable
	just so that it can be added to the all nodes table in Demo */
    public void step(SimState state) {}

    private String name;
    public java.lang.String	getName()	{ return name; }
    public void	setName​(java.lang.String name)	 { this.name = name; }


    public String report() {	
	String s = "[" + getName()+ " has received orders for " + everReceivedOrders + " u";

	s += "]";
       return wrap(s);
   }

    
}
