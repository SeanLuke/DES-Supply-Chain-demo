package  edu.rutgers.pharma3;

import sim.des.*;

import sim.engine.*;
import java.util.*;

import  edu.rutgers.util.Util;

/**
  A splitter divides its input into several streams, 
  sending a specified percent of its input into each receiver.
**/

public class Splitter extends If {


    private static class RData {
	/** fractions[j] specifies the fraction of the input that will
	    be sent to the j-th receiver */
	final double fraction;
	/** How much resource has been given to this receiver so far */
	double given=0;
	RData(double f) { fraction = f; }
    }

    private HashMap<Receiver, RData> data = new HashMap<>();
	
    void throwDoNotUse()        {
        throw new RuntimeException("Splitters do not respond to addReceiver(Receiver).  Instead, use addReceiver(Receiver, fraction).");
    }

    public Splitter(SimState state, Entity typical)        {
        super(state, typical);
	setName("Split");
    }
                  
    public boolean addReceiver(Receiver receiver) {
	throwDoNotUse();
	return false;
    }
    
    public boolean addReceiver(Receiver receiver, double fraction)      {
	if (data.containsKey(receiver))  throw new IllegalArgumentException("Duplicate addReceiver())");
	if (fraction < 0) throw new IllegalArgumentException("Fractions must be non-negatitve; given " + fraction);
	data.put(receiver, new RData(fraction));
	setName("Splitter("+showRatios()+")");
	return super.addReceiver(receiver);
    }

    public boolean removeReceiver(Receiver receiver)        {
	data.remove(receiver);
	return super.removeReceiver(receiver);
    }


    double totalAccepted=0;
    int cnt1=0, cnt2=0;

    /** This is called after a successful accept() by a downstream receiver */
    public void selectedOfferAccepted(Receiver receiver, Resource originalResource, Resource revisedResource) {
	cnt2++;
	RData d = data.get(receiver);
	if (d==null) throw new  IllegalArgumentException("Unknown receiver: " + receiver);
	double givenAmt=0;

	/*
	if (originalResource==null) { // this is how they handle entities...
	    givenAmt = (lastBatch==null)? 1: lastBatch.getContentAmount();
	} else if (originalResource  instanceof CountableResource) {
	    givenAmt = originalResource.getAmount() -  revisedResource.getAmount();
	} else
	*/
	if (originalResource instanceof Batch) {
	    givenAmt = ((Batch)originalResource).getContentAmount();
	} else { // Entity. We know that one was accepted
	    givenAmt = 1;// originalResource.getAmount();
	    //if (givenAmt!=1) throw new  IllegalArgumentException("We thought Entity.getAmount() always returns 1...");
	}	
	totalAccepted += givenAmt;
	d.given  += givenAmt;
    }

    //private Batch lastBatch = null;
    
    /** Decides to which receiver this batch of resource should be given.
	This method is called by Provider.offerReceivers(ArrayList<Receiver>),
	and the receiver selected by this method is used as an argument in 
	Provider.offerReceiver(Receiver, double).

	@param receivers  This should be exactly the array from Provider.receivers, or this method will break.
     */    
    public Receiver selectReceiver(ArrayList<Receiver> receivers, Resource amount) {
	cnt1 ++;
	if (receivers.size()==0) throw new IllegalArgumentException("No receivers!");
	
	int sumF=0;
	for(Receiver r: receivers) {
	    RData d = data.get(r);
	    if (d==null) throw new IllegalArgumentException("Unknown receiver: " + r);	   
	    sumF += d.fraction;
	}
	if (sumF == 0) throw new IllegalArgumentException("All fractions are zero!");
	

	// How much "stuff" are we offering?
	double amt = 
	    (amount instanceof Batch)? ((Batch)amount).getContentAmount() :
	    amount.getAmount();

	//lastBatch = (amount instanceof Batch)? (Batch)amount : null;
	
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
	//System.out.println("Chose: " +chosenR);
	if (chosenR==null)  throw new IllegalArgumentException("Something wrong: chose null out of "+ receivers.size()+" receivers!");
	return chosenR;
    }
        
    public String toString()
        {
        return "Splitter@" + System.identityHashCode(this) + "(" + (getName() == null ? "" : getName()) + typical.getName() + ", " + typical + ")";
        }

    /** Does nothing.  There's no reason to step a Splitter. */
    //public void step(SimState state)        {
        // do nothing
//}

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

    
    public String report() {
	Vector<String> v=new Vector<>();
	for(Receiver r: data.keySet()) {
	    RData d = data.get(r);
	    v.add("To " + r.getName() + ", " +Util.ifmt(d.given));
	}
	String s = "Split ("+showRatios()+") "+Util.ifmt(totalAccepted)+" units: ";

	s += String.join("; ", v);
	
	return s;
    }

 

}


