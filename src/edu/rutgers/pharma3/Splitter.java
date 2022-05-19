package  edu.rutgers.pharma3;

import sim.des.*;

import sim.engine.*;
import java.util.*;

/**
  A splitter divides its input into several streams, 
  sending a specified percent of its input into each receiver.
**/

public class Splitter extends If {
  
    /** Local copy of super.receivers (Receivers registered with the provider) */
    ArrayList<Receiver> myReceivers = new ArrayList<Receiver>();
  
    /** fractions[j] specifies the fraction of the input that will
	be sent to the j-th receiver */
    ArrayList<Double> fractions = new ArrayList<Double>();
    	
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
	if (myReceivers.contains(receiver)) throw new IllegalArgumentException("Duplicate addReceiver())");
	if (fraction < 0) throw new IllegalArgumentException("Fractions must be non-negatitve; given " + fraction);
	fractions.add(fraction);
	myReceivers.add(receiver);
	boolean result = super.addReceiver(receiver);
        return result;
    }

    Vector<Double> givenToEach = new Vector<Double>();

    public boolean removeReceiver(Receiver receiver)        {
	int j = myReceivers.indexOf(receiver);
	if (j<0)  return false;
	fractions.remove(j);
	myReceivers.remove(j);
	if (givenToEach.size()>j) givenToEach.remove(j);
        return super.removeReceiver(receiver);
    }


    double totalAcecepted=0;

    private double lastAmt = 0;
    private int chosenJ = -1;
    
    /** This is a wrapper around Filter.offerReceiver(Receiver receiver, double atMost),
	as "If" has no method of this name. Is used so that we can adjust "givenToEach" amounts
	after we know that offerReceiever() has been successful
	(i.e. the receiver has accepted the resource)
     */
    protected boolean offerReceiver(Receiver receiver, double atMost) {
	// This method gets called by Provider.offerReceiver(...)
	// with an argument that has been computed by Splitter.selectReceiver(),
	// and that  Splitter.selectReceiver() call is supposed to have
	// set lastAmt and chosenJ. Or at least that's the idea!
	boolean z = super.offerReceiver(receiver, atMost);
	//System.out.println("Splitter: offerReceiver done; chosenJ="+ chosenJ +", for amt=" + lastAmt);
	if (z) {
	    // FIXME: should use receiver.getLastAcceptedOffers().get(0) instead
             

	    
	    totalAcecepted+= lastAmt;
	    double x = givenToEach.get(chosenJ) + lastAmt;
	    givenToEach.set(chosenJ, x);
	}
	return z;
    }


    
    /** Decides to which receiver this batch of resource should be given.
	This method is called by Provider.offerReceivers(ArrayList<Receiver>),
	and the receiver selected by this method is used as an argument in 
	Provider.offerReceiver(Receiver, double).

	@param receivers  This should be exactly the array from Provider.receivers, or this method will break.
     */    
    public Receiver selectReceiver(ArrayList<Receiver> receivers, Resource amount) {
 
	if (receivers.size()!=myReceivers.size()) throw new IllegalArgumentException("Receivers array size mismatch");
	int k=0;
	for(Receiver r: receivers) {
	    if (r!=myReceivers.get(k))  throw new IllegalArgumentException("Receivers array  mismatch in position " +k);
	    k++;
	}

	
	if (givenToEach.size() < fractions.size()) {
	    int j = givenToEach.size();
	    givenToEach.setSize( fractions.size());
	    while( j<givenToEach.size()) givenToEach.set(j++, new Double(0));	    
	}

	int sumF=0;
	for(Double x: fractions)	    sumF += x;

	if (receivers.size()==0) throw new IllegalArgumentException("No receivers!");
	if (receivers.size()!= fractions.size()) throw new AssertionError("receivers.size()!=fractions.size()");
	if (sumF == 0) throw new IllegalArgumentException("All fractions are zero!");

	// How much "stuff" are we offering?
	// Here we set lastAmt for later use in offerReceiver (if accept() succeeds)
	double amt = lastAmt =
	    (amount instanceof Batch)? ((Batch)amount).getContentAmount() :
	    amount.getAmount();

	chosenJ = -1;
	double minR = 0;
	
	for(int j=0; j< givenToEach.size(); j++) {
	    if (fractions.get(j)==0) continue;
	    double r = (givenToEach.get(j)+amt)/fractions.get(j);	    
	    if (chosenJ < 0 || r < minR) {
		chosenJ = j;
		minR = r;
	    }
	}
	//System.out.println("Chose j="+ chosenJ +", for amt=" + lastAmt);
	Receiver recv = receivers.get(chosenJ);
	return recv;
    }
        
    public String toString()
        {
        return "Splitter@" + System.identityHashCode(this) + "(" + (getName() == null ? "" : getName()) + typical.getName() + ", " + typical + ")";
        }

    /** Does nothing.  There's no reason to step a Splitter. */
    public void step(SimState state)
        {
        // do nothing
        }
        
    //    boolean refusesOffers = false;
    //	public void setRefusesOffers(boolean value) { refusesOffers = value; }
    //    public boolean getRefusesOffers() { return refusesOffers; }


    public String report() {
	String s = "Split "+	totalAcecepted+" ba: ";
	Vector<String> v=new Vector<>();
	for(int j=0; j< givenToEach.size(); j++) {
	    v.add("To " + myReceivers.get(j).getName() + ", " + givenToEach.get(j));
	}
	s += String.join("; ", v);
	
	return s;
    }

 

}


