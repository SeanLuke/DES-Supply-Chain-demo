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

class MedTech implements Named, BatchProvider, Reporting, Steppable {

    /** Production nodes (including suppiers) to whom the order
	must be transmitted */
    final Production[] prod;
    final double[] factors;
    
    /**
       @param _prod To whom orders will be sent
       @param _factors How much the order to each node should be
       scaled. Typically, each value in the array is 1.0 (or a
       slightly larger value, to account for wastage). However, some
       values may be different, e.g. because we use 2 units of product
       X to make a unit of product Y, or because we distribute the
       order between several producers.
     */
    MedTech(String _name, Production[] _prod, double[] _factors) {
	setName(_name);
	prod = _prod;
	if (_factors==null) {
	    _factors = new double[prod.length];
	    for(int j=0; j<_factors.length; j++) _factors[j] = 1;
	} else if (_factors.length!=prod.length) throw new IllegalArgumentException();
	factors = _factors;
    }

    double everReceivedOrders = 0;
    
    
    /** Despite the name, it does not actually feed anything to the receiver,
	but requests the producer to produce some products which will eventually
	propagate to the receiver */       
    public double feedTo(Receiver r, double amt) {
	if (amt<0) throw new IllegalArgumentException(getName() +".feedTo(" + amt+")");

	int j=0;
	for(Production p: prod) {
	    p.addToPlan( Math.round(amt * factors[j++]));
	}
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
	String s = "[" + getName()+ " has received orders for " + (long)everReceivedOrders + " u";

	s += "]";
       return wrap(s);
   }

    
}
