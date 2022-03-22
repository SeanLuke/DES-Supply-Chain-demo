package  edu.rutgers.pharma3;

import sim.des.*;

import sim.engine.*;
import java.util.*;

/**
  A splitter divides its input into several streams, 
  sending a specified percent of its input into each receiver.
**/

public class Splitter extends Provider implements Receiver    {
    private static final long serialVersionUID = 1;


    /** Local copy of super.receivers (Receivers registered with the provider) */
    ArrayList<Receiver> myReceivers = new ArrayList<Receiver>();
  
    /** fractions[j] specifies the fraction of the input that will
	be sent to the j-th receiver */
    ArrayList<Double> fractions = new ArrayList<Double>();
	
    public Resource getTypicalReceived() { return typical; }
	public boolean hideTypicalReceived() { return true; }

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

    public boolean removeReceiver(Receiver receiver)        {
	int j = myReceivers.indexOf(receiver);
	if (j<0)  return false;
	fractions.remove(j);
	myReceivers.remove(j);
        return super.removeReceiver(receiver);
    }

    Vector<Double> givenToEach = new Vector<Double>();
    
    /**
       Accepts the resource, which must be a composite Entity, and offers the resources in its
       storage to downstream receivers.
    **/
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost)        {       
    	if (getRefusesOffers()) { return false; }
        if (!typical.isSameType(amount)) throwUnequalTypeException(amount);

        if (isOffering()) throwCyclicOffers();  // cycle
        
        if (!(atLeast >= 0 && atMost >= atLeast))
        	throwInvalidAtLeastAtMost(atLeast, atMost);

	if (givenToEach.size() < fractions.size()) {
	    int j = givenToEach.size();
	    givenToEach.setSize( fractions.size());
	    while( j<givenToEach.size()) givenToEach.set(j++, new Double(0));	    
	}

	int sumF=0;
	for(Double x: fractions)	    sumF += x;

	if (myReceivers.size()==0) throw new IllegalArgumentException("No receivers!");
	if (myReceivers.size()!= fractions.size()) throw new AssertionError("receivers.size()!=fractions.size()");
	if (sumF == 0) throw new IllegalArgumentException("All fractions are zero!");


	double amt = amount.getAmount();
	int chosenJ = -1;
	double minR = 0;
	
	for(int j=0; j< givenToEach.size(); j++) {
	    if (fractions.get(j)==0) continue;
	    double r = (givenToEach.get(j)+amt)/fractions.get(j);	    
	    if (chosenJ < 0 || r < minR) {
		chosenJ = j;
		minR = r;
	    }
	}
       	
	Receiver recv = myReceivers.get(chosenJ);
	return recv.accept(this, amount, atLeast, atMost);
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
        
    boolean refusesOffers = false;
	public void setRefusesOffers(boolean value) { refusesOffers = value; }
    public boolean getRefusesOffers() { return refusesOffers; }


    public String report() {
	String s = "Split: ";
	Vector<String> v=new Vector<>();
	for(int j=0; j< givenToEach.size(); j++) {
	    v.add("To " + myReceivers.get(j).getName() + ", " + givenToEach.get(j));
	}
	s += String.join("; ", v);
	
	return s;
    }


}


