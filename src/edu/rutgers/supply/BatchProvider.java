package edu.rutgers.supply;

import java.util.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

/** An interface describing a source that can send, on demand, a
    specified amount of Batch resource to a specified destination.
    This interface may be implemented by objects implementing
    various types of "pools" in SC-2 -- basically, warehouses
    that fulfill orders sent from other entities.
 */
public interface BatchProvider {
       /** Handles the request from a downstream receiver (such as the
	EndConsumer) to send to it one or several batches, totaling at
	least a specified amount of stuff. Discards any expired (or
	near-expired) batches identified during the process.
	@param amt the requested amount (in units)
	@return the actually sent amount, in units. (Can be a bit more
	than amt due to batch size rounding, or it  can be less due to the
	shortage of product).
     */
    double feedTo(Receiver r, double amt);

}

