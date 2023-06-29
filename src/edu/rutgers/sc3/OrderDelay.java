package  edu.rutgers.sc3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.supply.*;
import edu.rutgers.util.*;

/** Contract negotiation delay. It is used to model the delay between the
    time a customer decides to order a product and the time when the
    order is accepted for execution. 

    <p> A ContractDelay is created by a Production object who received
    delayed orders. The Production object should also set the delay
    distribution as specified by the config file. Whenever
    Production.request(Order order) is called, the Production object
    puts the order (packaged into an Entity) into the OrderDelay;
    once the order has ripened, the OrderDelay feeds it into its internal
    sink, which triggers Production.doAddToPlan().
 */
class OrderDelay extends Delay {
    /** A wrapper around an Order object, which is stored in the
	info field
     */
    private static class OrderPaper extends Entity {
	/** Creating the prototype */
	OrderPaper() {
	    super("OrderPaper");
	}
	/** Wraps an Order into an OrderPaper */
	OrderPaper(Order order) {
	    super(proto);
	    setInfo(order);
	}
	Order getOrder() {
	    return (Order)getInfo();
	}
    }

    static OrderPaper proto = new OrderPaper();

    /** This is where orders are put when they are "ripe" */
    private static class OrderSink extends Sink {
	final Production dest;
	OrderSink(SimState state, Production _dest) {
	    super(state, proto);
	    dest = _dest;
	}

	public boolean accept(Provider provider, Resource resource, double atLeast, double atMost) {
	    if (!(resource instanceof OrderPaper)) throw new AssertionError("Not an OrderPaper");
	    dest.doAddToPlan( ((OrderPaper)resource).getOrder());
	    return true;
	}
	
    }
    
    private final OrderSink orderSink;
    
    OrderDelay(SimState state, Production dest) {
	super(state, proto);
	setName(dest.getName() + ".OrderDelay");
	addReceiver( orderSink = new OrderSink(state, dest));
    }

    void enter(Order order) {
	boolean z = accept(null, new OrderPaper(order), 1, 1);
	if (!z) throw new AssertionError("Delay.accept failed");
    }

}
