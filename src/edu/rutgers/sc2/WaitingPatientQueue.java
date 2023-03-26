package  edu.rutgers.sc2;

import java.util.Vector;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;
import edu.rutgers.supply.*;
import edu.rutgers.supply.Disruptions.Disruption;

/** Patients arrive to this queue randomly "from the street", and wait
    here for the EE and DS to be provided to them */
public class WaitingPatientQueue extends sim.des.Queue implements Named, Reporting {
    AbstractDiscreteDistribution dailyArrivalDistribution;
    
    public WaitingPatientQueue(SimState state, Config config) throws IllegalInputException {
	super(state, Patient.prototype);
	setName(Util.cname(this));
	ParaSet para = config.get(getName());

	double avgDaily = para.getDouble("avgDailyArrival");
	dailyArrivalDistribution = new Poisson(avgDaily, state.random);
	
    }


    /* Puts more patients into the queue */
    public void step(SimState state) {
	int todaysArrivals  = dailyArrivalDistribution.nextInt();
	for(int j=0; j<todaysArrivals; j++) {
	    Patient p = new Patient();
	    Provider provider = null;  // why do we need it?		
	    if (!accept(provider, p, 1, 1)) throw new IllegalArgumentException("WPQ must accept");

	}
    }

      public String report() {	
	  String s = "[" + getName(); //+ " has received orders for " + everReceivedOrders + " u";

	s += "]";
       return wrap(s);
   }

    /*
    Patient getFirst() {
	return (Patient)entities.getFirst();
    }

    private boolean remove(Batch b) {
	return entities.remove(b);
    }
   
    boolean offerToMe(Receiver r, Patient p) {
	return offerReceiver(r, p);
    }
    */
    
}
