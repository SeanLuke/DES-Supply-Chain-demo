package edu.rutgers.supply;

import java.util.*;

import sim.engine.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Describes the communication between 2 Pools. In SC2, a reference
    to a Channel is put into each Order object; this is used for
    internal accounting of various kinds. */
public class Channel {
    /** A Pool or MedTech */
    final public BatchProvider2 sender;
    /** Typically a Delay object representing a shipping delay */
    final public Receiver receiver;
    /** Typically, "pool1.pool2". It is used to identify any entries in the disruption scenario file related to this
	channel. */
    final public String name;

    public Channel( BatchProvider2 _sender,
	     Receiver _receiver,
	     String _name) {
	sender = _sender;
	receiver = _receiver;
	name = _name;
	   
    }

    
    /** The amount "on back order"; needs to be sent as soon as it becomes available */
    /*
    public double needToSend() {
	double sum = 0;
	for(OnOrder.Entry e: ordersToFill)k sum += e.emount;
	return sum;
    }
    */

    public Timed infoHaltedUntil = new Timed();    


    /** Checks if there is a "StopInfoFlow" disruption in effect for this channel. */
    public boolean isInfoHalted(double now) {
	return infoHaltedUntil.isOn( now );
    }

    /** Outstanding orders, as seen by the sender. */
    //Vector<OnOrder.Entry> ordersToFill = new Vector<>();

}
