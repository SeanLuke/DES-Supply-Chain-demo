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
import edu.rutgers.supply.Disruptions.Disruption;

class MedTech implements Named, BatchProvider2, Reporting, Steppable {

    final SimState state;

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
    MedTech(SimState _state, String _name, Production[] _prod, double[] _factors) {
	state = _state;
	setName(_name);
	prod = _prod;
	if (_factors==null) {
	    _factors = new double[prod.length];
	    for(int j=0; j<_factors.length; j++) _factors[j] = 1;
	} else if (_factors.length!=prod.length) throw new IllegalArgumentException();
	factors = _factors;
    }

    double everReceivedOrders = 0;
    
    
    /** Consumers can call this method
	to request the producer to produce some products which will eventually
	propagate to the receiver

	@param r Actually ignored
    */       
    //public double feedTo(Receiver r, double amt) {
    public void request(Order order) { //Channel channel, double amt) {
	Channel channel = order.channel;
	Receiver r = channel.receiver;
	double amt = order.amount;
	if (amt<0) throw new IllegalArgumentException(getName() +".feedTo(" + amt+")");
	double now = state.schedule.getTime();
	if (channel.isInfoHalted(now)) {
	    if (!Demo.quiet) System.out.println("At " + now+", " + getName() + " ignored request(" + channel +","+amt+") because of StopInfoFlow disruption");	 
	    return;
	}

	
	int j=0;
	for(Production p: prod) {
	    p.addToPlan( Math.round(amt * factors[j++]));
	}
	// FIXME: should also order some raw materials for the production node,
	// so that it won't have to rely on safety stocks
	everReceivedOrders += amt;
	
    }

    /** Nothing is done here. This class implements Steppable
	just so that it can be added to the all nodes table in Demo */
    public void step(SimState state) {
	double now = state.schedule.getTime();
	Disruptions.Type type = Disruptions.Type.StopInfoFlow;
	for(Channel channel: outChannels) {
	    for(Disruption d: ((Demo)state).hasDisruptionToday(type, channel.name)) { 
		channel.infoHaltedUntil.enableUntil( now+d.magnitude );
		if (!Demo.quiet)  System.out.println("At t=" + now + ", Channel "+ channel.name +" started disruption '"+type+"' until " + (now+d.magnitude));
	    }
	}
    }

    /** Whither, and how, this Pool may send stuff. The list is compiled based on calls to feedTo() */
    Set<Channel> outChannels = new HashSet<>();

    /** This can be called when the channel is first created, so that we'll be ready to
	impose a "StopInfoFlow" disruption on it even before it's first use */
    public void registerChannel(Channel channel) {
	outChannels.add(channel);
    }


    private String name;
    public java.lang.String	getName()	{ return name; }
    public void	setName​(java.lang.String name)	 { this.name = name; }


    public String report() {	
	String s = "[" + getName()+ " has received orders for " + (long)everReceivedOrders + " u";

	s += "]";
       return wrap(s);
   }

    
}
