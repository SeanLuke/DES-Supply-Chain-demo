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

/** Patients are being treated (wear EE and DS) here. Each patient has
    his own randomly-chosen treatment time; thus it's a Delay and not
    a SimpleDelay.
*/
public class ServicedPatientPool extends Delay implements Named, Reporting {
    AbstractDistribution serviceTimeDistribution;
    WaitingPatientQueue wpq;
    Pool eeHEP, dsHEP;

    final private Source eeReturner;
    
    /** The 2 receivers accept patients in 3 different situations:
	finished treatment; EE device broke (but repairable); EE device
	dead. */

    
    
    /** Only accepts patients if the treatment has come to a successful end */
    class CuredPatientSink extends MSink {
	CuredPatientSink(SimState state) { super(state, Patient.prototype);}
	public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	    Patient p = (Patient)amount;
	    EE ee = p.getEE();


	    //System.out.println("CPS.accept trying " + ee);
	    if (!ee.patientCured()) return false;
	    everCured++;
	    // return the device to the pool
	    ee.finishUse(p);
	    if (!eeHEP.accept(eeReturner, ee, 1, 1)) throw new AssertionError();
	    return true;
	}
    }

    /** How many EE devices have died on us since the beginning of the simulation */
    double everEeDied=0;
    /** How many times an EE device was sent to repairs */
    double everEeBroke=0;
    /** How many patients have been released after completing treatment */
    double everCured=0;
    /** How many patients were sent back to the waiting queue because
	their EE broke or died */
    double everAnnoyed=0;

   
    /** For patients whose EE device breaks on them. They are routed back to WPQ */
    class AnnoyedPatientProbe extends Probe {
 	AnnoyedPatientProbe(SimState state) {
	    super(state);
	    addReceiver(wpq);
	}
	
	public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	    Patient p = (Patient)amount;
	    EE ee = p.getEE();


	    //System.out.println("APP.accept trying " + ee);
	    
	    if (ee.patientCured()) return false;
	    
	    // return the device to the pool
	    if (ee.eeDied()) { // Discard the device
		everEeDied += 1;
		if (!deadEESink.accept(null, ee, 1,1)) throw new AssertionError();
	    } else 	if (ee.eeBroken()) { // Broken, but repairable
		everEeBroke += 1;
		if (!repairPool.accept(null, ee, 1,1)) throw new AssertionError();
	    } else  throw new AssertionError("Unknown EE device state");

	    ee.finishUse(p);
	    everAnnoyed++;
	    return super.accept(provider, amount, atLeast,  atMost);
	}
     }
    
    
    Delay repairPool;
    
    //MSink repairQueue;
    MSink deadEESink;
    
    public ServicedPatientPool(SimState state, Config config, WaitingPatientQueue _wpq, Pool _eeHEP, Pool _dsHEP
			       ) throws IllegalInputException, IOException {
	super(state, Patient.prototype);
	setName(Util.cname(this));
	wpq = _wpq;
	eeHEP = _eeHEP;
	dsHEP = _dsHEP;
	ParaSet para = config.get(getName());

	serviceTimeDistribution = para.getDistribution("serviceTime", state.random);
	AbstractDistribution repairTimeDistribution = para.getDistribution("repairTime", state.random);

	addReceiver(new CuredPatientSink(state));
	addReceiver(new AnnoyedPatientProbe(state));

	repairPool = new Delay( state,  eeHEP.getPrototype());
	repairPool.setDelayDistribution(  repairTimeDistribution );
	repairPool.addReceiver( eeHEP);
	
	deadEESink = new MSink(state, eeHEP.getPrototype());
	//wpq.addReceiver(this);
	charter=new Charter(state.schedule, this);
        String moreHeaders[] = {"servicedPatients"};
        charter.printHeader(moreHeaders);

	eeReturner = new Source(state, eeHEP.getPrototype());

	eeHEP.addVolunteerSender(eeReturner);
	eeHEP.addVolunteerSender(repairPool);
	
	initialFill( state, config);
    }

    /* This method has a dual role. First, since it's an
       (auto-scheduled) delay, it's automatically called every time
       when a patient is released from treatment (at the end of his
       treatment time).  Here, the super.step() part takes care of
       getting items out. Second, we schedule it on daily intervals,
       so that it can take patients from the queue, fit them with EE
       and DS, and put them into the treatment process (this Delay).

     */
    public void step(SimState state) {
	super.step(state); 

	// this part really only needs to be done daily... but it's ok to
	// check more often as well
	while(wpq.getAvailable()>0) {
	    boolean z = wpq.provide(this);
	    if (!z) break;
	}

	double now = state.schedule.getTime();
	if (Math.round(now) == now) { // our own daily schedule
	    dailyChart();
	}

    }

    

    int everAccepted=0;

    /** Accepts a patient into treatment, if EE and DS units are available.
	@param amount a Patient object
	@return true if the patient has been accepted into treatment; false
	if he wasn't, due to lack of equipment.	
     */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {

	  if (!(amount instanceof Patient)) throw new IllegalArgumentException("SPP cannot accept a non-Patient: " + amount);
	  Patient p = (Patient)amount;
	  if (eeHEP.getAvailable()==0) return false;
	  if (dsHEP.getAvailable()==0) return false;


	  if (dsHEP.consumeSome(1) != 1) throw new AssertionError("Why is dsHEP empty?!");
	  EE ee = eeHEP.extractOneEE();
	  double now = state.schedule.getTime();
	  p.startTreatment( now, ee, serviceTimeDistribution);

	  //System.out.println("SPP.accept, p=" + p);

	  //-- don't do that, as it will clear the heap!
	  //setDelayTime( ee.getEEInfo().delayTime);
	  	  
	  boolean z = super.accept( provider, p,  atLeast,  atMost);
	  
	  if (!z) throw new AssertionError("Delay did not accept");
	  everAccepted++;

	  //System.out.println("SPP.accept: everAccepted=" + everAccepted +"; delay has " + getDelayed()); //super.report());
	  
	  return z;
      }

    
    /** Extracts the individualized delay (time to spend in the
	treatment pool), which has been precomputed for the patient */
    protected double getDelay(Provider provider, Resource amount) {
	if (!(amount instanceof Patient)) throw new IllegalArgumentException();
	Patient p= (Patient)amount;
	double d = p.getEE().getEEInfo().delayTime;
	//	System.out.println("DEBUG: return delayTime="+d);
	return d;
    }
 
    //     protected boolean offerReceivers(ArrayList<Receiver> receivers)
    // protected boolean offerReceivers() 
   

    public String report() {	
	String s = "[" + getName();//+ " has received orders for " + everReceivedOrders + " u";
	s += "; accepted " + (long)everAccepted + " patients (plus the init pop="+initPop+"); currently treated=" + (long)getDelayed();
	s += "; patients cured=" +  (long)everCured +
	    ", sent back to wait=" + (long)everAnnoyed +
	    ". EE destroyed=" + (long)everEeDied +
	    ", sent to repair=" + (long)everEeBroke;
	s += ". EE still in repair pool="+ (long)repairPool.getDelayed();

	s += "]";
	return wrap(s);
   }

    int initPop = 0;
    
    /** Initialization. Used on startup to fill in the SPP with
	patients, approximating a steady-state population.
	The purpose of this is to reduce start-up effects in the
	behavior of the system.

	<p>Since this method is run during the startup, the current
	time at this point is -1; therefore, we add 1 to all lifetimes
	etc, to ensure that they will come "ripe" at positive times.
    */
    public void initialFill(SimState state,
			    Config config
			    //, ParaSet para
			    ) 
	throws IllegalInputException {
	String name2 = getName() + ".init";
	ParaSet para = config.get(name2);
	if (name2==null) return;

	//System.out.println("DEBUG: init, now=" +state.schedule.getTime() );
	//System.exit(1);

	// How many days ago did we start filling the SPP?
	Double initDepth = para.getDouble("depth", null);
	if (initDepth==null) throw new IllegalInputException("Para set " + name2 + " must have field `depth'");
	if (initDepth<1) throw new IllegalInputException("Para set " + name2 + " invalid value for  `depth'; must be positive");
	final double now = 1; // start with 1 (rather than 0), because it works better in Mason this way
	for(int ago=0; ago<initDepth; ago++) {
	    int n = wpq.computeDaysArrivals();

	    for(int i=0; i<n; i++) {
		Batch eeb = ((Batch)eeHEP.getPrototype()).mkNewLot(1, now);
		EE ee = new EE(eeb, true);
		

		
		Patient p = new Patient();
		boolean stillIn = p.startTreatment( now, ee, serviceTimeDistribution, ago, +1);
		if (stillIn) {
		    Provider provider = null;  // why do we need it?
		    // We call super.accept(), rather than accept(),
		    // because the patient has already be fitted with an EE
		    boolean z = super.accept( provider, p, 1, 1);
		    if (!z) throw new AssertionError();
		    initPop ++;
		}
	    }
	    
	}

	
    }


    private Charter charter;
    private void dailyChart() {               
        double[] data = {getDelayed()};   
        charter.print(data);
    }

    
}
