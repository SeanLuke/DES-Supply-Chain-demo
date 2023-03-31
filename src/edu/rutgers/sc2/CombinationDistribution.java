package  edu.rutgers.sc2;

import edu.rutgers.supply.*;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

//import edu.rutgers.util.*;
//import edu.rutgers.supply.Disruptions.Disruption;

/** This distribution is built so that the value it returns is the sum
    of the n values returned by the underlying distribution. If n
    is sufficiently large, the result is similar to that of a
    normal distribution, of course.

    Thus if the underlying distribution describes the time needed to perform an operation,
    the CombinationDistribution describes the time needed to perform a sequence of n such
    operations.
    */
public class CombinationDistribution extends AbstractContinuousDistribution {
    final AbstractDistribution d0;
    final int n;
    
    CombinationDistribution(AbstractDistribution _d0, int _n) {
	d0 = _d0;
	if (d0==null) throw new IllegalArgumentException();
	n = _n;
    }
    public double nextDouble() {
	double sum=0;
	for(int j=0; j<n; j++) {
	    sum += d0.nextDouble();
	}
	return sum;
    }

    public int nextInt() {
	return (int)Math.round(nextDouble());
    }
    
}

    

