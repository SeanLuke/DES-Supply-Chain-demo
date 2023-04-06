package edu.rutgers.supply;

import edu.rutgers.supply.*;

import sim.des.*;

import sim.engine.*;
import java.util.*;

import edu.rutgers.util.Util;

/**
  A Splitter divides its input into several streams, 
  sending a specified percent of its input into each receiver. 

  <p>A Splitter normally has several (two or more) Receivers attached
  to it. (Well, you can have just 1 Receiver of course, but that will
  be a useless trivial splitter). A non-negative real number is
  associated with each Receiver, indicating the desired portion
  ("target fraction") of the incoming stream of product that should go
  to that Receiver. The Splitter tries to divide the incoming stream
  of product between its receivers so that the fraction received by
  each receiver is proportional to the number associated with that
  receiever.

  <P>A Splitter can work both with a fungible resource (CountableResource)
  and with a Batch resource. Either way, it applies fractions to the
  amount of the underlying resource, rather than to the number of batches.

  <p>A Splitter never "divides"

**/

public class Splitter extends If {

    /** An auxiliary class used for accounting of the product going
	to a particular Receiever */
    public static class RData {
	/** specifies the fraction of the input that will
	    be sent to this receiver */
	public final double fraction;
	/** How much resource has been given to this receiver so far */
	protected double given=0;
	public RData(double f) { fraction = f; }
	public RData(RData z) { this(z.fraction); }
    }

    /** Accounting structures for all receivers */
    public HashMap<Receiver, RData> data = new HashMap<>();
	
    void throwDoNotUse()        {
        throw new RuntimeException("Splitters do not respond to addReceiver(Receiver).  Instead, use addReceiver(Receiver, fraction).");
    }

    public Splitter(SimState state, Entity typical)        {
        super(state, typical);
	setName("Splitter of " + typical.getName());
    }


    /** One should not use this method; use
	addReceiver(Receiver* receiver, double fraction) instead */
    public boolean addReceiver(Receiver receiver) {
	throwDoNotUse();
	return false;
    }

    /** Adds one more destination (output channel) to this splitter. One
	must make addReceiver() calls for all destinations before starting
	using the splitter.
        @param receiver A receiver to add
	@param fraction A non-negative number indicating the portion of the
	incoming stream of resource that should be sent to this Receiver.  If the fraction values for all Receivers sum to 1.0, then each one, indeed, is simply equal to the target fraction of the resource going to the associated receiver. Otherwise, we internally normalize by dividing each "fraction" value by the sum of all "fraction" values. So for example if we have 3 receivers with fractions 10, 30, 10, then they will receive (approximately) 20%, 60%, and 20% of the entire input stream.
	
     */
    public boolean addReceiver(Receiver receiver, double fraction)      {
	if (data.containsKey(receiver))  throw new IllegalArgumentException("Duplicate addReceiver())");
	if (fraction < 0) throw new IllegalArgumentException("Fractions must be non-negatitve; given " + fraction);
	data.put(receiver, new RData(fraction));
	setName("Splitter("+showRatios()+")");
			  
	return super.addReceiver(receiver);
    }


    /** One can use this method, but it's not recommended to do this after
	the Splitter started to be used, because the arithmetic will be
	rather confused. */
    public boolean removeReceiver(Receiver receiver)        {
	data.remove(receiver);
	return super.removeReceiver(receiver);
    }

    private double totalAccepted=0;

    /** This is called after a successful accept() by a downstream receiver */
    public void selectedOfferAccepted(Receiver receiver, Resource originalResource, Resource revisedResource) {
	RData d = data.get(receiver);

	//System.out.println("Select; Report for " + getName() + "\n" + report());
	
	if (d==null) throw new IllegalArgumentException("Unknown receiver: " + receiver);
	double givenAmt=0;

       
	if (originalResource  instanceof CountableResource) {
	    givenAmt = originalResource.getAmount() -  revisedResource.getAmount();
	} else	if (originalResource instanceof Batch) {
	    givenAmt = ((Batch)originalResource).getContentAmount();
	} else { // Entity. We know that one was accepted
	    givenAmt = 1;// originalResource.getAmount();
	    //if (givenAmt!=1) throw new  IllegalArgumentException("We thought Entity.getAmount() always returns 1...");
	}	
	totalAccepted += givenAmt;
	d.given  += givenAmt;
    }

    public double computeSumF(Collection<Receiver> receivers) {
	double sumF=0;
	for(Receiver r: receivers) {
	    RData d = data.get(r);
	    if (d==null) throw new IllegalArgumentException("Unknown receiver: " + r);	   
	    sumF += d.fraction;
	}
	return sumF;
    }


    
    /** Decides to which receiver this batch of resource should be given.
	This method is called by Provider.offerReceivers(ArrayList<Receiver>),
	and the receiver selected by this method is used as an argument in 
	Provider.offerReceiver(Receiver, double).

	@param receivers  This should be exactly the array from Provider.receivers, or this method will break.
	@param amount This could be some amount of a fungible resource (CountableResource) or a Batch. Either way, the Splitter will send the entire thing to one chosen Receiver. The choice is made to keep the receivers' fractions of the received resource as close to the target values as possible.
     */    
    public Receiver selectReceiver(ArrayList<Receiver> receivers, Resource amount) {
	if (receivers.size()==0) throw new IllegalArgumentException("No receivers!");
	
	double sumF= computeSumF(receivers);
	if (sumF == 0) {
	    throw new IllegalArgumentException(toString()+": All fractions are zero!");
	}
	
	// How much "stuff" are we offering?
	double amt = 
	    (amount instanceof Batch)? ((Batch)amount).getContentAmount() :
	    amount.getAmount();
	
	Receiver chosenR = null;
	double minZ = 0;

	for(Receiver r: receivers) {
	    RData d = data.get(r);
	    if (d.fraction==0) continue;
	    double z = (d.given+amt)/d.fraction;	    
	    if (chosenR==null || z < minZ) {
		chosenR = r;
		minZ = z;
	    }
	}

	if (chosenR==null)  throw new IllegalArgumentException("Something wrong: chose null out of "+ receivers.size()+" receivers!");
	return chosenR;
    }
        
    public String toString()
        {
	    return "Splitter@" + System.identityHashCode(this) + "(" + (getName() == null ? "" : getName()) + getTypicalReceived().getName() + ")";
        }


    private String showRatios() {
	Vector<String> q=new Vector<>();

	double maxF = 0;
	for(Receiver r: data.keySet()) {
	    RData d = data.get(r);
	    maxF = Math.max(maxF, d.fraction);
	}

	double m = (maxF>1)? 1: 100;
	
	for(Receiver r: data.keySet()) {
	    RData d = data.get(r);
	    q.add(Util.ifmt(d.fraction * m));
	}
	return String.join(":", q);	
    }

    /** Reports how much product has been sent to each channel */
    public String report() {
	Vector<String> v=new Vector<>();
	for(Receiver r: data.keySet()) {
	    RData d = data.get(r);
	    v.add("To " + r.getName() + " [f="+d.fraction+"], " +Util.ifmt(d.given));
	}
	String s = "Split ("+showRatios()+") "+Util.ifmt(totalAccepted)+" units: ";

	s += String.join("; ", v);
	
	return s;
    }

 

}


