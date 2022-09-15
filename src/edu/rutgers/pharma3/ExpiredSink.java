package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

/** An ExpiredSink only accepts Batches which it considers expired or near-expired. It can be used
    to "purge" (near-)expired batches from the front section of a provider's Queue, much like a 
    medicinal leech is used to remove "bad blood" from a patient.

    This class exists because there are many points in our supply chain where expired lots have to be 
    destroyed once they are identified during the usual course of business.

    This class extends from MSink in order to collect stats.
 */
class ExpiredSink extends MSink  {

    final int spareDays;

    /**
    	@param _spareDays A batch is considered "bad" (nearly expired,
	and subject to discarding) if it's within this many days from
	expiration. This value can be 0 or positive.
    */
    public ExpiredSink(SimState state, Batch typical, int _spareDays) {
	super(state,typical);
	spareDays = _spareDays;	
    }

    /** Accepts (nearly-)expired batches, rejects good one.
     */
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	if (!(resource instanceof Batch)) throw new IllegalArgumentException("ExpiredSink can only take Batch resources");
	Batch b = (Batch)resource;
	double t = state.schedule.getTime();

	if (!b.willExpireSoon(t, spareDays)) return false;
	if (!super.accept( provider,  resource, atLeast,atMost)) throw new AssertionError("Sinks ought not to refuse stuff!");
	return true;	
    }


    /** Scans the batches of a provider of Batches to find the first
	"good" batch (one that's not about to expire). In so doing,
	purges the provider from any "bad" (nearly expired) batches
	found in the process, by consuming them and removing them from
	the provider.  This method can be used if we use this MSink as
	a dumping place for expired batches.

	Note: this method could be refactored as a method of the
	Provider class, with the signature 
	Entity 	Provider.getNonExpiredBatch(Receiver consumerOfBadBatches) 

	The Receiver in question would be required to accept() only
	bad entities (it can be an ExpiredSink), so that the method
	would return the first entity that the Receivers refused to
	accept.

	@param p A provider of batches whose content we want to test

	@param The first "good" batch found in the provider, or null if none has been found.
    */
    Batch getNonExpiredBatch(Provider p, LinkedList<Entity> entities) {
	while( p.getAvailable()>0) {
	    Batch b = (Batch)entities.getFirst();
	    if (!accept(p, b, 1, 1))  return b;	
	    entities.remove(b);
	}
	return null;
    }



}
