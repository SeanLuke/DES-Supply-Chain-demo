package edu.rutgers.supply;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;


/** A class that takes batches of any size, and splits them into
    batches of specified size (possibly with some odd-size ones)
 */
public class BatchDivider extends sim.des.Queue {

    final double outBatchSize;
    
    public BatchDivider(SimState state, Resource typical, double _outBatchSize)        {
        super(state, typical);
	if (!(typical instanceof Batch)) throw new IllegalArgumentException("Expected a Batch type");
        setName("BatchDivider of " + typical.getName());
	outBatchSize = _outBatchSize;
	setOffersImmediately(true);
 
    }

    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	if (!(amount instanceof Batch)) throw new IllegalArgumentException("Expected a Batch type");
	Batch b = (Batch) amount;
	while(b.getContentAmount()>outBatchSize) {
	    Batch c = b.split( outBatchSize);
	    if (!super.accept(provider, c,  atLeast, atMost)) throw new IllegalArgumentException("Queue is supposed to accept everything");
	}
	return super.accept(provider, b,  atLeast, atMost);
	
    }
 
}
