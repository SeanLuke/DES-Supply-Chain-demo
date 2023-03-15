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
public class ServicedPatientPool extends Delay implements Named {
    AbstractDistribution serviceTimeDistribution;
    WaitingPatientQueue wpp;
    
    public ServicedPatientPool(SimState state, Config config, Entity typicalPatient,  WaitingPatientQueue _wpp) throws IllegalInputException {
	super(state, typicalPatient);
	setName(Util.cname(this));
	wpp = _wpp;
	ParaSet para = config.get(getName());

	serviceTimeDistribution = para.getDistribution("serviceTime", state.random);

	
    }




    
    /* Takes patients from the queue, fits them with EE and DS, and puts
       them into the treatment process (this Delay)
     */
    public void step(SimState state) {

	while(wpp.getAvailable()>0) {
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


    
}
