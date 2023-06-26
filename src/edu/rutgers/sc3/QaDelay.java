package  edu.rutgers.sc3;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import  edu.rutgers.util.*;


/** A Quality Assurance Delay serves as a delay unit that identifies
    some portion of the input as "bad", and removes it from the channel 
    before it's offered to the downstream consumer. Optionally, it
    can also direct some portion of the input to the "rework" receiver.
 */
public class QaDelay extends SimpleDelay
    implements Reporting, Reporting.HasBatches
{

    /** This is non-null if we have item-by-item testing, with only
	same units discarded from each batch. If it is present, then
	discardProb and reworkProb must both be zeros. It is expected
	that this distribution only returns numbers within [0:1]
	range. For each batch being tested, a number is drawn from
	this distribution, and the corresponding fraction of the batch
	is discarded.
   */
    final AbstractDistribution faultyPortionDistribution;

    /** If any of these is non-zero, then we do whole-batch
	test-and-discard. In this case, faultyPortionDistribution must
	be null.

	The two values indicate with what probability a batch is
	discarded, and with what probability a batch is sent back to
	the production stage for reworking.
     */
    final double discardProb, reworkProb;

    /** If true, the discard decision is made by applying "discardProb"
	(and "reworkProb") separately to each item, rather than by simply
	computing the number of discarded item by multiplying the
	batch size by value drawn from faultyPortionDistribution
    */
    final boolean unitLevel;

    /** Unit (pill) counts for the 3 directions of flow. */
    double badResource = 0, reworkResource=0, releasedGoodResource=0;
    public double getBadResource() { return badResource; }
    public double getReleasedGoodResource() { return releasedGoodResource; }
    public double getReworkResource() { return reworkResource; }

    public double getEverReleased()  { return releasedGoodResource; }
  
    
    long badResourceBatches = 0, releasedGoodResourceBatches=0;
 
    final MSink discardSink;


    private Timed faultRateIncrease = new Timed();

    /** This is used by a disruptor to reduce the quality of the
	products produced by this unit over a certain time
	interval. This only should be used for fungible products
	(CountableResource); for Batch products, it's preferable to
	use the similar method in ProdDelay, so that the "poor
	quality" will be associated with the manufacturing date,
	rather than the QA date.
    */
     void setFaultRateIncrease(double x, Double _untilWhen) {
	faultRateIncrease.setValueUntil(x,_untilWhen);
    }
 
  
    final Resource prototype;

    
    /** @param typicalBatch A Batch of the appropriate type (size does not matter), or a CountableResource
	@param _faultyPortionDistribution If non-null, then _discardProb and double _reworkProb must be zero, and vice versa.
     */
    public QaDelay(SimState state, Resource typicalBatch,  double _discardProb, double _reworkProb, AbstractDistribution _faultyPortionDistribution, boolean _unitLevel) {
	super(state, typicalBatch);
	prototype = typicalBatch;
	setName("QaDelay("+typicalBatch.getName()+")");
	setOfferPolicy(Provider.OFFER_POLICY_FORWARD);
	discardProb = _discardProb;
	reworkProb= _reworkProb;
	faultyPortionDistribution = _faultyPortionDistribution;
	unitLevel = _unitLevel;
  	discardSink = new MSink( state, typicalBatch);

	//	System.out.println("DEBUG: Created QaDelay(" + getName()+"," + discardProb + "," + reworkProb);

	
	if (faultyPortionDistribution!=null) {
	    if (discardProb!=0 || reworkProb !=0) throw new IllegalArgumentException("Cannot set both faultyPortionDistribution and discardProb/reworkProb on the same QaDelay!");
	} 
    }

    /** Creates a QaDelay based on the parameters from a specified ParaSet.
	@return a new QaDelay object, or null if the para set contains no 
	parameters for one.
     */
    static public QaDelay mkQaDelay(Config config, ParaSet para, SimState state, Resource outResource) throws IllegalInputException, IOException {	
	// See if "faulty" in the config file is a number or
	// a distribution....
	double faultyProb=0;	
	AbstractDistribution faultyPortionDistribution=null;

	if (para.get("faulty")==null) {
	    return null;
	} else if (para.get("faulty").size()==1) {
	    faultyProb = para.getDouble("faulty");
	} else {
	    faultyPortionDistribution = para.getDistribution("faulty",state.random);
	}
	double reworkProb = para.getDouble("rework", 0.0);	    

	if (faultyPortionDistribution !=null && faultyProb+reworkProb!=0) throw new IllegalInputException("For " + para.name +", specify either faulty portion distribution or faulty+rework probabilities, but not both!");    

	boolean unitLevel = (faultyPortionDistribution ==null);
	unitLevel = para.getBoolean("qaUnitLevel", unitLevel);
	if (faultyPortionDistribution !=null && unitLevel) throw new IllegalInputException("In " + para.name +", cannot have both faulty portion distribution and qaUnitLevel=true");
	
	QaDelay qaDelay = new QaDelay(state, outResource, faultyProb, reworkProb, faultyPortionDistribution, unitLevel);

	String reworkName = para.name + ".rework";
	ParaSet para2 = config.get(reworkName);
	if (para2 != null) {
	    qaDelay.reworkStage = new Production(state, reworkName, config,
						 new Resource[0],
						 (Batch)outResource);
	    qaDelay.setRework(qaDelay.reworkStage.prodStage());
	}
	
	//qaDelay.setDelayDistribution(para.getDistribution("qaDelay",state.random));
	//Double delayTime = para.getDouble("qaDelay", null);
	//if (delayTime==null) throw new IllegalInputException("Missing value for " + para.name +".qaDelay in config file!");
	//System.out.println("Creating QaDelay." + para.name + "(" + delayTime+")");
	//qaDelay.setDelayTime(delayTime);
	return qaDelay;
    }

    /** The optional node that includes production and QA, to which
	some batches may be sent for rework. This is null (and faulty
	batches are sent back to the main production unit for rework),
	unless "name.rework" para set exists in the config file */
    Production reworkStage;
    

    /** This is where some bad batches may be sent for rework.
	This is either the main production unit (default), or reworkStage
	(if so specified in the config file). */
    Receiver sentBackTo=null;


    /** Call this method if the QA process, in addition to discarding 
	some items, can also send some items back to the factory
	for reprocessing.  It only makes sense to call this method
	if the parameter file provides reworkProb, which indicates the
	probability of a lot experiencing this outcome.
	@param  _sentBackTo Where to send some of the faulty products for rework.
     */
    void setRework( Receiver _sentBackTo) {
	sentBackTo=_sentBackTo;
    }

    /** Deciding how many units from a batch are to be discarded,
	and how many are to be sent to be reworked. 

	@param amt Size of the batch
	@param li The lot info (if available), from which fault rate
	adjustment can be obtained. It can be null for a fungible product
	@return { numberToDiscard, numberToRework}
     */
    private double[] unitLevelDiscard(double amt, LotInfo li) {
	double now = state.schedule.getTime();
	double discard=0, rework=0;
       	if (faultyPortionDistribution!=null) {
	    double r =faultyPortionDistribution.nextDouble();
			//CombinationDistrinution.nextDouble(faultyPortionDistribution, (int)amt);
	    if (r<0) r=0;
	    if (r>1) r=1;

	    double rEffective = Math.min(r + faultRateIncrease.getValue(now), 1.0);
		
	    discard = Math.round( amt * rEffective);
	} else if (unitLevel) { // true unit-level decision

	    double dp = discardProb;
	    if (li!=null) 	dp    += li.getIncreaseInFaultRate();
	    dp = Math.min(dp, 1);

	    // The probability that the unit is "not good", i.e must be
	    // either discarded or reworked
	    double notGoodProb = Math.min( dp + reworkProb, 1);
	    if ( notGoodProb  >0) {
		
		int n = (int)Math.round(amt);
		if (n != amt) throw new IllegalArgumentException("Cannot perform unit-level QA decisions, because batch size is not integer: " + amt);
		for(int j=0; j<n; j++) {		    
		    boolean isBad = state.random.nextBoolean(notGoodProb);
		    if (isBad) {
			boolean willRework = state.random.nextBoolean( reworkProb/notGoodProb);
			if (willRework) rework++;
			else discard++;
		    }
		}
	    }
	}
	return new double[] { discard, rework };
    }

    
    /** This is a wrapper over the standard Provider.offerReceiver(),
	which reduces the amount of available stuff (resource) we are
	to offer to the receiver. The reduction can be due to the QA
	inspection removing some poor quality items, or due to to
	damage by mice and weevils, or to other adverse effects.

	<p> //FIXME: this method has the assumption that the Receiver
	will take everything offered to it. This is OK for the Pharma3
	(SC-1) model; but, in general, if this assumption does not
	hold, the repeated offers will repeatedly find bad items in
	the already-checked pool. This can be fixed by creating a
	separate Queue for the already-checked stuff, and passing it
	to Receiver.accept() calls.
	
	<P>
	Types of decision:
	<ul>
	<li>Entire batch accepted or discarded. This is specified by a scalar,
	discardProb (and reworkProb, if needed), that contains the probability
	of the decision.
	<li>Part of the batch is discarded, with a decision based by drawing a single number from a distribution (faultyPortionDistribution)
	<li>Part of the batch is discarded, with decisions based on the level of individual items. For this discardProb  (and reworkProb, if needed) is provided, and the flag .... is set to true.
	
	</ul>
       
      
       		
    */
    protected boolean offerReceiver(Receiver receiver, double atMost) {

	if (Demo.verbose)   System.out.println(getName() + ".offerReceiver(" +receiver+", " + atMost+")");

	boolean showAge = false; // !Demo.quiet;
	boolean z;

	double now = state.schedule.getTime();

	// Can we discard part of the lot, or do we only do whole-lot discards?
	boolean partialLotDiscard = (faultyPortionDistribution!=null) || unitLevel;      

	if (partialLotDiscard) {
	    //if (faultyPortionDistribution!=null) {
	    // Discarding part of the batch based on a percentage
	    // drawn from a random distribution. 
	    double amt, discard=0, rework=0;
	    
	    if (entities == null) {
		CountableResource cr = resource;
		amt = Math.min( cr.getAmount(), atMost);		
		if (amt==0) return false; // this happens sometimes, triggered by SimpleDelay.step()
		
		double[] dr = unitLevelDiscard(amt, null);

		discard = dr[0];
		if (dr[1]>0) throw new AssertionError("No support for rework");
		// The faulty product is destroyed, so we decrease the resource now
		cr.decrease(discard);
		
		double atMost1 = atMost - discard;
		if (atMost1<0) {
		    throw new AssertionError("Error in atMost arithmetic");
		} else if (atMost1==0 || cr.getAmount()==0) {
		    // everything was discarded, no good product left to send
		    z = true;
		} else {		    		
		    z = super.offerReceiver(receiver, atMost1);
		}
		
	    } else {
						
		Batch e = (Batch)entities.getFirst();
		LotInfo li = e.getLot();
		if (showAge) {
		    double age =  (now - li.getEarliestAncestorManufacturingDate());
		    System.out.println(getName() + " at " + now + " testing batch aged " + age + "; " + li);
		}
				       		
		amt = e.getContentAmount();
		double[] dr = unitLevelDiscard(amt, li);
				
		discard = dr[0];
		rework = dr[1];

		if (rework>0) {
		    Batch rb =e.split(rework);
		    z = super.offerReceiver(sentBackTo, rb);
		    reworkResource += rework;
		    reworkBatches++;
		}
		
		e.getContent().decrease( discard);
		z = super.offerReceiver(receiver, e);
		entities.remove(e);	// manually remove e from entities?
	    }
	    
	    if (!z) throw new IllegalArgumentException("QaDelay cannot be used with a receiver ("+receiver.getName()+") that refuses offers.  amt="+amt+", atMost=" +atMost+", discard="+discard);
	    
	    badResource +=  discard;
	    releasedGoodResource += (amt-discard);

	    if (discard>0) 	    badBatches++;
	    if (discard<amt)	    releasedBatches ++;
	    
	    //System.out.println("F=" + faulty +", G=" + (amt-faulty));
	    
	} else {  // Entire-lot discard, using discardProb (with unitLevel=false)
	    if (unitLevel) throw new AssertionError("unitLevel");
	    if (entities == null) throw new IllegalArgumentException("pharma3.QaDelay with faultyProb only works with Batches!");

	    Batch b = (Batch)entities.getFirst();
	    double amt = b.getContentAmount();
	    
	    boolean willDiscard=false, willRework=false;
	    // The probability that the lot must be discarded
	    double dp = discardProb + b.getLot().getIncreaseInFaultRate();
	    dp = Math.min(dp, 1);

	    // The probability that the lot is "not good", i.e must be
	    // either discarded or reworked
	    double notGoodProb = Math.min( dp + reworkProb, 1);
	    if ( notGoodProb  >0) {
		
		boolean isBad = state.random.nextBoolean(notGoodProb);
		if (isBad) {
		    willRework = state.random.nextBoolean( reworkProb/notGoodProb);
		    willDiscard = !willRework;
		}
	    }

	    z =
		willRework ? super.offerReceiver(sentBackTo, b):
		willDiscard? super.offerReceiver(discardSink, b):
		super.offerReceiver(receiver, b);

	    if (!z) throw new IllegalArgumentException("The expectation is that the receivers for QaDelay " + getName() + " never refuse a batch");
	    
	    if (showAge) {
		LotInfo li = b.getLot();
		String code = willRework? "r" :willDiscard? "b" : "g";
		li.addToMsg("[tested " + code + " @" + now +" ]");
		double age =  (now -li.getEarliestAncestorManufacturingDate());
		System.out.println(getName() + " at " + now + " testing batch aged " + age + "; " + li );
	    }
				     
	    if (willRework) {
		reworkResource += amt;
		reworkBatches++;
	    } else if (willDiscard) {
		badResource +=  amt;
		badBatches++;
	    } else {
		releasedGoodResource += amt;
		releasedBatches++;
	    }
	}
	
	return z;

    }

    /** Statistics on batches sent into each of the 3 possible directions */
    long reworkBatches=0, badBatches=0, releasedBatches=0;
 

    /** Still under processing + at the output */
    double hasBatchesOnBothSides() {
	return getDelayed() + getAvailable();
    }

    public String hasBatches() {
	String s = "" + (long)getDelayed();
	if (getAvailable()>0) s += "+"+(long)getAvailable();
	return s;
    }

    public String report() {

	String s = "(in QA= " +  hasBatches() +	" ba; discarded="+(long)badResource  +
	" ("+badBatches+" ba)";
	if (reworkProb>0) s+= "; rework="+(long)reworkResource +
				      " ("+reworkBatches+" ba)";

	s += "; good=" + (long)releasedGoodResource+" ("+releasedBatches+" ba))";
	return s;
	
    }

    /** Computes the parameters of this QaDelay node, for use in
	planning. The average output/input ratio can be computed as	
	<div align="center">
	gamma = (1-alpha-beta)/(1-beta),
	</div>
	where alpha=faultRate, beta=reworkProbability.

	@param return (alpha, beta, gamma), where gamma=The average output/input ratio
     */
    double[] computeABG() {

	double mean = 0;
	if (faultyPortionDistribution!=null) {
	    mean = ParaSet.computeMean(faultyPortionDistribution);
	} else {
	    mean = discardProb;
	}
	double[] abg = {mean, reworkProb, (1-mean - reworkProb)/(1-reworkProb)};
	return abg;
	
    }

  
    
}
