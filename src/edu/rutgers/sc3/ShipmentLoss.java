package  edu.rutgers.sc3;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;
import edu.rutgers.supply.Disruptions.Disruption;

/** Destroys some shipments in a transportation delay (such as the one
    between production and QA in a Production node, or one between
    pools). */
public class ShipmentLoss {

    /** Destroys some shipments in a transportation delay (such  as the one between production and QA
	in a Production node, or one between pools).

	All nodes with the same timestamps are interpreted as a single shipment.

	@param transDelay The SimpleDelay object, representing shipments currently in transit, which is to be affected by a disruption. (That is, some shipments from it will disappear).
    */
    static double disruptShipments(SimState state, String name, SimpleDelay transDelay) {
	Disruptions.Type type = Disruptions.Type.ShipmentLoss;
	double now = state.schedule.getTime();

	double sumStolen = 0;
	for(Disruption d:  ((Demo)state).hasDisruptionToday(type, name)) {
	    int m = (int)d.magnitude;
	    
	    //for(int j=0; j<m && transDelay.getAvailable()>0; j++) {
	    //  boolean z = transDelay.provide(stolenShipmentSink);
	    //  if (!z) throw new AssertionError("Sink failed to accept");
	    //}
	    DelayNode[] nodes = transDelay.getDelayedResources();
	    //int allCnt=0;
	    int loseCnt=0, nowStolen=0, nodeCnt=0;
	    double timestamp = Double.NEGATIVE_INFINITY;
	    for(DelayNode node: nodes) {
		if (node.isDead()) continue;
		//allCnt++;
		//System.out.println("DEBUG: Deleting shipment, size=" + Batch.getContentAmount(node.getResource()));

		DelayNode q = node;
		//	for( DelayNode q = node;  q!=null; q=q.getNext()) {		
		nowStolen += Batch.getContentAmount(q.getResource());
		nodeCnt++;
		q.setDead(true);
		    //}		


		double t = q.getTimestamp();
		if (t!=timestamp) {
		    timestamp = t;
		    loseCnt++;

		}

		if (loseCnt >= m) break;
	    }
	    if (!Demo.quiet)  System.out.println("At t=" + now + ", Transport link  "+ name +", disruption '"+type+"' could affect up to " + m + " shipments out of "+nodes.length+" nodes; actually deleted " + loseCnt +" shipments "+
						     "("+nodeCnt+" nodes) "+
						     "("+nowStolen+" u)");

	    sumStolen += nowStolen;
		

	    
	}
	return sumStolen;
	
    }

}
