package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;

/** The Untrusted Suppplier Pool has an inexhaustible supply of stuff,
    into which some "illicit" units are mixed in.
 */
public class UntrustedPool extends Provider
    implements Reporting, Named, BatchProvider 	{
    
    /** Similar to typical, but with storage. In this case, it's batches of packaged drug  */
    protected final Batch prototype;

    /** Standard batch size */
    final double batchSize;
    /** From this batch, the percentage of bad pills mixed into each batch is drawn */
    final AbstractDistribution illicitDis;

    protected Charter charter;
    
    
    public UntrustedPool(SimState state, String name, Config config, Batch resource) throws IllegalInputException, IOException {
	
	super(state, resource);	
	prototype = resource;
		
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	batchSize = para.getDouble("batch");
	illicitDis = para.getDistribution("illicit",state.random);
	if (illicitDis == null)  throw new  IllegalInputException("For element named '" + name +"', the 'illicit' distribution is not specified");
 	charter=new Charter(state.schedule, this);
	charter.printHeader("sentToday");

    }
    
    private double everSent = 0, everSentIllicit=0;
    /** How many units have been sent since the previous-day
	reporting. The amount is incremented on sending, and will be
	zeroed after today's reporting.
    */
    double sentToday=0;

    /** Satisfies a pull request, sendig a specified amount of product to 
	a specified receiver. The amount may be rounded up to a full batch
	for convenience of the subsequent accounting.
    */
    public double feedTo(Receiver r, double amt) {
	double sent = 0, sentBad=0;
   
	Batch b;
	while(sent < amt) {
	    double now = state.schedule.getTime();
	    Batch whiteHole = prototype.mkNewLot(batchSize, now);
	    double bad = Math.round( batchSize * illicitDis.nextDouble());
	    whiteHole.getLot().illicitCount = bad;
	    if (!r.accept(this, whiteHole, 1, 1)) throw new AssertionError("Pool "+r.getName()+"  did not accept");
	    double q = whiteHole.getContentAmount();	    
	    sent += q;
	    sentToday += q;
	    sentBad += bad;
	}	

	everSent += sent;
	everSentIllicit += sentBad;
	return sent;
    }

    /** Just writes the numbers to the CVS file */
    public void step(SimState state) {
	charter.print(sentToday);
	sentToday=0;
    }

   public String report() {
	String s = "[" + getName()+ " has sent " + everSent + " u, including illicit= "+everSentIllicit+" u]";
       return wrap(s);
   }

    
}
