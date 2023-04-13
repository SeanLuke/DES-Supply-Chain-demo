package  edu.rutgers.supply;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

/** An auxiliary class for computing certain derived distributions */
public class DistributionUtil {

    /** Estimated Max */
    final double M;
    //final AbstractContinousDistribution dis;
    final AbstractDistribution dis;
    
    public DistributionUtil(AbstractDistribution _dis) {
	//if (!(_dis instanceof AbstractContinousDistribution)) throw new IllegalArgumentException();
	//dis = (AbstractContinousDistribution)_dis;
	dis = _dis;
	/*
	double x = 1;
	while(dis.cdf(x)<0.999) {
	    if (x>1e6) throw new IllegalArgumentException("Cannot find max of " + dis);
	    x *= 2;
	}
	M = x;
	*/
	double w = 1;
	final int N = 1000;
	for(int j=0; j<N; j++) {
	    double x = dis.nextDouble();
	    if (x>w) w=x;
	}
	M = 2*w;
	
    }

    public double nextAgedDouble() {
	double ago = Math.random() * M;
	//double x;
	while( (ago -= dis.nextDouble()) > 0) {}
	return -ago;
	   
    }
}
