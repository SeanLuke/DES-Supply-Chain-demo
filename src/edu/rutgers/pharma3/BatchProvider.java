package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** 
 */
public interface BatchProvider {
       /** Handles the request from a downstream receiver (such as the EndConsumer) to send to it 
	a number of batches, totaling at least a specified amount of stuff. Discards any 
	expired (or near-expired) batches identified during the process.
	@param amt the requested amount (in units)
	@return the actually sent amount, in units. (Can be a bit more
	than amt due to batch size rounding, or it  can be less due to the
	shortage of product).
     */
    double feedTo(Receiver r, double amt);

}

