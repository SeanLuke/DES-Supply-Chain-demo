package  edu.rutgers.pharma3;

import sim.des.*;

import sim.engine.*;
import java.util.*;

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
    }
                  
    public boolean addReceiver(Receiver receiver) {
	throwDoNotUse();
	return false;
    }
    
    public boolean addReceiver(Receiver receiver, double fraction)      {
	if (data.containsKey(receiver))  throw new IllegalArgumentException("Duplicate addReceiver())");
	if (fraction < 0) throw new IllegalArgumentException("Fractions must be non-negatitve; given " + fraction);
	data.put(receiver, new RData(fraction));
	return super.addReceiver(receiver);
    }

    public boolean removeReceiver(Receiver receiver)        {
	data.remove(receiver);
	return super.removeReceiver(receiver);
    }


    double totalAccepted=0;


    /** This is called after a successful accept() by a downstream receiver */
    public void offerSuccessful(Receiver receiver, Resource originalResource, Resource revisedResource) {
	RData d = data.get(receiver);
	if (d==null) throw new  IllegalArgumentException("Unknown receiver: " + receiver);
	double givenAmt=0;
	if (originalResource  instanceof CountableResource) {
	    givenAmt = originalResource.getAmount() -  revisedResource.getAmount();
	} else if (originalResource instanceof Batch) {
	    givenAmt = ((Batch)originalResource).getContentAmount();
	} else { // Entity. We know that one was accepted
	    givenAmt = originalResource.getAmount();
	    if (givenAmt!=1) throw new  IllegalArgumentException("We thought Entity.getAmount() always returns 1...");
	}	
	totalAccepted += givenAmt;
	d.given  += givenAmt;
    }


    
    /** Decides to which receiver this batch of resource should be given.
	This method is called by Provider.offerReceivers(ArrayList<Receiver>),
	and the receiver selected by this method is used as an argument in 
	Provider.offerReceiver(Receiver, double).

	@param receivers  This should be exactly the array from Provider.receivers, or this method will break.
     */    
    public Receiver selectReceiver(ArrayList<Receiver> receivers, Resource amount) {
	if (receivers.size()==0) throw new IllegalArgumentException("No receivers!");
	
	int sumF=0;
	for(Receiver r: receivers) {
	    RData d = data.get(r);
	    if (d==null) throw new IllegalArgumentException("Unknown receiver: " + r);	   
	    sumF += d.fraction;
	}
	if (sumF == 0) throw new IllegalArgumentException("All fractions are zero!");
	
	    //	if (receivers.size()!= fractions.size()) throw new AssertionError("receivers.size()!=fractions.size()");


	// How much "stuff" are we offering?
	// Here we set lastAmt for later use in offerReceiver (if accept() succeeds)
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
	System.out.println("Chose: " +chosenR);
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
        
    public String report() {
	String s = "Split "+	totalAccepted+" units: ";
	Vector<String> v=new Vector<>();
	for(Receiver r: data.keySet()) {
	    v.add("To " + r.getName() + ", " + data.get(r).given);
	}
	s += String.join("; ", v);
	
	return s;
    }

 

}


