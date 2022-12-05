package  edu.rutgers.pharma3;

import edu.rutgers.supply.*;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

/** An ExpiredSink only accepts Batches which it considers expired or
    near-expired. It can be used to "purge" (near-)expired batches
    from the front section of a provider's Queue, much like a
    medicinal leech is used to remove "bad blood" from a patient.

<P>
    This class exists because there are many points in our supply
    chain where expired lots have to be destroyed once they are
    identified during the usual course of business.

<p>
    This class extends from MSink in order to collect all-time stats.
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
	@param provider does not matter a lot
	@param resource a Batch to be tested
	@return true on acceptance (i.e. if the batch was found to be expired)
     */
    public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	if (!(resource instanceof Batch)) throw new IllegalArgumentException("ExpiredSink can only take Batch resources");
	Batch b = (Batch)resource;
	double t = state.schedule.getTime();

	if (!b.willExpireSoon(t, spareDays)) return false;
	if (!super.accept( provider,  resource, atLeast,atMost)) throw new AssertionError("Sinks ought not to refuse stuff!");
	return true;	
    }
    
    Batch getNonExpiredBatch(Provider p, LinkedList<Entity> entities) {
	return getNonExpiredBatch( p,  entities, new double [1]);
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

	@param p A provider of Batches whose content we want to test

	@param entities This is Provider.entities of p. It is passed so that
	the removal of bad batches can be effected.

	@param removedAmt This must be double[1]. It is used as an output parameter; on return, it will contain the total amount of underlying resource removed
	from the provider.

	@return The first "good" batch found in the provider, or null if none has been found.
	
    */
    Batch getNonExpiredBatch(Provider p, LinkedList<Entity> entities, double removedAmt[]) {
	removedAmt[0] = 0;
	while( p.getAvailable()>0) {
	    Batch b = (Batch)entities.getFirst();
	    if (!accept(p, b, 1, 1))  return b;		    
	    entities.remove(b);
	    removedAmt[0] += b.getContentAmount();
	}
	return null;
    }

    /** @return A short stats message, or an empty string if nothing has ever been discarded as expired */
    String reportShort() {
	return (everConsumed==0)? "" : "(Discarded expired=" + everConsumed + " u = " + everConsumedBatches + " ba)";
    }


}
