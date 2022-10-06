package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;


/** A simple anomaly detector. It watches the daily value of
    everReceived of (e.g.) some Pool, and notices unusual drops in the
    daily increment amount.
 */
class SimpleDetector {
    Vector<Double> everReceivedHistory = new Vector<>();
    Vector<Boolean> excludableHistory = new Vector<>();
    Vector<Boolean> anomalyHistory = new Vector<>();


    double  startupTime = 30;
    double window = 15;
    double allowedDeviation = 0.20;

    /** This can be thrown when the historical data are not sufficient
	to make an estimate of the "normal" level */
    static class NoRecentDataException extends Exception {
	//public NoRecentDataException(String msg) {
	//    super(msg);
	//}
    }

    /** How much, on average, we received per day over the last "window" days? */
    double averageBaseline() throws  NoRecentDataException {
	int j0 = everReceivedHistory.size() - 1;
	double sum = 0;
	int n = 0;

	int maxk = (int)window;
	
	for(int k = 1; k<= maxk; k++) {
	    int j = j0 - k;
	    if (j<=0) break;
	    if (excludableHistory.get(j) || anomalyHistory.get(j)) {
		maxk++; // skip an "ignorable" day, and look deeper into the past instead
	    } else {
		sum += (everReceivedHistory.get(j) -everReceivedHistory.get(j-1));
		n ++;
	    }	    
	}
	if (n==0) throw new NoRecentDataException();
	return sum / n;
    }
    

    /** @param _now current time
	@param everReceived the current value of everReceived in the pool or buffer being monitored
    */
    boolean test(double _now, double everReceived, boolean excludable) {
	int now = (int)Math.round(_now + 0.001); // just in case
	if (now != everReceivedHistory.size()) throw new IllegalArgumentException("Unexpected now=" + _now +"; needed " + everReceivedHistory.size());
	everReceivedHistory.add(everReceived);
	excludableHistory.add(excludable);
	anomalyHistory.add(false);

	if (now < startupTime) return false;
	if (excludable) return false;
	double base;
	try {
	    base = averageBaseline();
	} catch(NoRecentDataException ex) {
	    if (!Demo.quiet) System.out.println("Cannot run anomaly detection (no recent good data) at t=" + _now);
	    return false;
	}
	double receivedToday = everReceived -  everReceivedHistory.get(now-1);
	if (receivedToday < (1-allowedDeviation)*base) {
	    if (!Demo.quiet) {
		String msg = anomalyHistory.get(now-1)? "Detect anomaly continuing  at t=" + _now:
		     "Detect anomaly starting at t=" + _now;
		System.out.println(msg);
	    }
	    anomalyHistory.set( now, true);	    
	    return true;
	}
	return false;
	
    }

    
}
