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
    
    public ServicedPatientPool(SimState state, Config config, WaitingPatientQueue _wpq, Pool _eeHEP) throws IllegalInputException {
	super(state, Patient.prototype);
	setName(Util.cname(this));
	wpq = _wpq;
	eeHEP = _eeHEP;
	ParaSet para = config.get(getName());

	serviceTimeDistribution = para.getDistribution("serviceTime", state.random);

	//wpq.addReceiver(this);
	
    }

    /* Takes patients from the queue, fits them with EE and DS, and puts
       them into the treatment process (this Delay)
     */
    public void step(SimState state) {

	while(wpq.getAvailable()>0) {
	    boolean z = wpq.provide(this);
	    if (!z) break;
	}
    }

    int everAccepted=0;

    
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {

	  if (!(amount instanceof Patient)) throw new IllegalArgumentException("SPP cannot accept a non-Patient: " + amount);
	  Patient p = (Patient)amount;
	  if (eeHEP.getAvailable()==0) return false;

	  //double c0 = eeHEP.getContentAmount();
	  
	  EE ee = eeHEP.extractOneEE();
	  p.equip(ee);
	  
	  boolean z = super.accept( provider, p,  atLeast,  atMost);
	  
	  if (!z) throw new AssertionError("Delay did not accept");
	  everAccepted++;
	  return z;
      }


    public String report() {	
	String s = "[" + getName();//+ " has received orders for " + everReceivedOrders + " u";
	s += "; accepted " + everAccepted + " patients; currently treated=" + getDelayed();

	s += "]";
	return wrap(s);
   }
    
}
