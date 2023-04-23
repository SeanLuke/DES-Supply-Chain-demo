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
    
    public WaitingPatientQueue(SimState state, Config config) throws IllegalInputException, IOException {
	super(state, Patient.prototype);
	setName(Util.cname(this));
	ParaSet para = config.get(getName());

	double avgDaily = para.getDouble("avgDailyArrival");
	dailyArrivalDistribution = new Poisson(avgDaily, state.random);
	
	charter=new Charter(state.schedule, this);
	String moreHeaders[] = {"waitingPatients"};
	charter.printHeader(moreHeaders);	
    }


    int computeDaysArrivals() {
	return dailyArrivalDistribution.nextInt();
    }

    /* Puts more patients into the queue. As a kludge, we report the
       number of waiting patients *before* new patients are added into
       the line, so that 0 will be reported if each day's new patients
       are serviced the same day. */
    public void step(SimState state) {
	sumWaiting += getAvailable();
	nWaiting++;
	dailyChart();


	int todaysArrivals =  computeDaysArrivals();
	for(int j=0; j<todaysArrivals; j++) {
	    Patient p = new Patient();
	    Provider provider = null;  // why do we need it?		
	    if (!accept(provider, p, 1, 1)) throw new IllegalArgumentException("WPQ must accept");

	}
    }

      public String report() {	
	  String s = "[" + getName();
	  s += "; waiting patients=" + (long)getAvailable();
	  s += "; waiting patients avg over time=" + (sumWaiting/nWaiting);

	s += "]";
       return wrap(s);
   }

   private Charter charter;
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

    public double sumWaiting=0;
    int nWaiting=0;
    
    /** Writes this days' time series values to the CSV file. 
	Does that for the safety stocks too, if they exist.
	Here we also check for the inflow anomalies in all
	buffers.  This method needs to be called from Production.step(),
	to ensure daily execution.
    */
    private void dailyChart() {
	
	double[] data = {getAvailable()};
   
	charter.print(data);
    }
    

    
}
