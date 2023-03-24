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

/** Patients are being treated (wear EE and DS) here. Each one has his own
    randomly-chosen treatment time; thus it's a Delay and not a SimpleDelay.
*/
public class ServicedPatientPool extends Delay implements Named, Reporting {
    AbstractDistribution serviceTimeDistribution;
    WaitingPatientQueue wpq;
    Pool eeHEP;
    
    public ServicedPatientPool(SimState state, Config config, Entity typicalPatient,  WaitingPatientQueue _wpq, Pool _eeHEP) throws IllegalInputException {
	super(state, typicalPatient);
	setName(Util.cname(this));
	wpq = _wpq;
	eeHEP = _eeHEP;
	ParaSet para = config.get(getName());

	serviceTimeDistribution = para.getDistribution("serviceTime", state.random);

	
    }

    /* Takes patients from the queue, fits them with EE and DS, and puts
       them into the treatment process (this Delay)
     */
    public void step(SimState state) {

	while(wpq.getAvailable()>0) {
	    //   if (hepEE.getAvailable()>0) {	    
	    //	Batch b=hepEE.getFirst();			
	    //}
	}
	/*
	int todaysArrivals  = dailyArrivalDistribution.nextInt();
	for(int j=0; j<todaysArrivals; j++) {
	    Patient p = new Patient();
	    Provider provider = null;  // why do we need it?		
	    if (!accept(provider, p, 1, 1)) throw new IllegalArgumentException("WPQ must accept");

	}
	*/
    }

    public String report() {	
	String s = "[" + getName();//+ " has received orders for " + everReceivedOrders + " u";

	s += "]";
	return wrap(s);
   }
    
}
