package edu.rutgers.util;

import java.io.*;
import java.util.*;

import ec.util.MersenneTwisterFast;
import sim.util.distribution.*;


/** A ParaSet object contains the parameters for one element of the 
    supply chain. Those may include, for example, the parameters 
    of a production and/or QA delay, batch size, throughput, etc.

    <p>When the configuration file is loaded, the value in the 
    first column of each line serves as the name of the ParaSet to
    which the data in the rest of the line belong.

    <P>The map maps the key (a string, from the 2nd column of the line) to a vector of
    strings, which represent the values from the 3rd and subsequent columns
*/

public class ParaSet extends HashMap<String, Vector<String>> {

    /** The name of the ParaSet, i.e. the name of the supply set element
	whose parameters this ParaSet contains.
     */
    final public String name;

    /** Creates an empty parameter set */
    public ParaSet(String _name) {
	name = _name;
    }

    /** This may refer to the "default" para set, or (for nested para sets)
	to the enclosing para set */

    private ParaSet parent = null;

    void setParent( ParaSet _parent) { parent = _parent; }
    ParaSet getParent() { return parent; }
    
    
    void add(CsvData.BasicLineEntry line) throws IllegalInputException {
	int nCol=line.nCol();
	if (nCol<3)  throw new IllegalInputException("Fewer than 3 columns in config line: " + line);
	if (!line.getCol(0).equals(name))  throw new IllegalInputException("ParaSet name mismatch: " + name + " vs " +  line.getCol(0));
	String key = line.getCol(1);
	Vector<String> v = new Vector<>();
	for(int j=2; j<nCol; j++) v.add(line.getCol(j));
	if (get(key)!=null) throwII(key, "Duplicate entry in the config file");
	put(key,v);
    }


    private void throwII(String key, String msg)throws IllegalInputException  {
	msg = "In the parameter set for object " + name + ", property " + key + ": " + msg;
	throw new IllegalInputException(msg);
    }

    public String getString(String key) throws IllegalInputException {
	String x = getString(key, null);
	if (x==null)  throwII(key, "Missing");
	return x;
    }

    public String getString(String key, String defVal) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null)  return defVal;
	if (v.size()!=1) throwII(key, "Expected exactly 1 data column");
	return v.get(0);	
    }

    /** Parses the kind of expressions that can occur in a real-value
	field. E.g. "3.333", "10/3", "10+3" etc. We needed this
	because Ben likes to express processing times in his parameter
	file as fractions (e.g.
	[0.75/ 2.718281828, 1.25 / 2.718281828])
	
        @param key just for use in error messages
	@param s  a real value or a fraction */
    public Double parseDoubleEx(String key, String s) throws IllegalInputException {

	String[] ops = {"/", "*", "+", "-"};
	String[] rops = {"/", "\\*", "\\+", "\\-"};

	for(int k=0; k<ops.length; k++) {
	    String op =  ops[k];
	    String rop = rops[k];
	
	    String tok[] = s.split(rop);
	
	    if (tok.length!=2 || tok[0].length()==0) continue;
	    try {
		Double a[] = new Double[tok.length];
		for(int j=0; j<tok.length; j++) {
		    a[j]= Double.parseDouble(tok[j].trim());
		}
		return op.equals("/")? a[0]/a[1]:
		    op.equals("*")? a[0]*a[1]:
		    op.equals("+")? a[0]+a[1]:
		    op.equals("-")? a[0]-a[1] : null;
	    } catch(Exception ex) {
		throwII(key, "Cannot parse as a simple expression: " + s);
	    }
	}

	try {
	    return Double.parseDouble(s);
	} catch(Exception ex) {
	    throwII(key, "Cannot parse as a real number: " + s);
	}	
	return null; // never reached   
    }

 
    public Double getDouble(String key, Double defVal) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null && parent!=null) v = parent.get(key);
	if (v==null) 	return defVal;
	if (v.size()!=1) throwII(key, "Expected exactly 1 data column");
	String s = v.get(0);
	return  parseDoubleEx(key, s);	
    }

    public Integer getInt(String key, Integer defVal) throws IllegalInputException {
	Double d = getDouble(key, null);
	if (d==null) return defVal;
	int n = d.intValue();
	if ((double)n != d.doubleValue()) throw new  IllegalInputException("For key '"+key+"' expected an integer value, found " + d);
	return n;
    }

    public double getDouble(String key) throws IllegalInputException {
	Double  x = getDouble(key, null);
	if (x==null)  throwII(key, "Missing");
	return x.doubleValue();
    }

    public double [] getDoubles(String key) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null)  throwII(key, "Missing");
	if (v.size()<1) throwII(key, "Expected at least 1 data column");
	double[] q = new double[v.size()];
	for(int j=0; j<q.length; j++) {
	    q[j] = parseDoubleEx(key, v.get(j));
	}
	return q;	
    }
    
    public long getLong(String key) throws IllegalInputException {
	Long x = getLong(key, null);
	if (x==null)  throwII(key, "Missing");
	return x.longValue();
	    
    }
    public Long getLong(String key, Long defVal) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null)  return defVal;
	if (v.size()!=1) throwII(key, "Expected exactly 1 data column");
	String s = v.get(0);
	try {
	    return Long.parseLong(s);
	} catch(Exception ex) {
	    throwII(key, "Cannot parse as a long int: " + s);
	    return null; // never reached
	}
	
    }


    public Boolean getBoolean(String key, Boolean defVal) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null)  return defVal;
	if (v.size()!=1) throwII(key, "Expected exactly 1 data column");
	String s = v.get(0);
	try {
	    return Boolean.parseBoolean(s);
	} catch(Exception ex) {
	    throwII(key, "Cannot parse as a boolean: " + s);
	    return null; // never reached
	}	
    }


    /** Returns the value of the specified parameter if it can be
	interpreted as a value of the specified enumerated type.
	If no parameter with the specified value has been supplied,
	or if its value cannot be interpreted as a value of the desired
	type, the supplied default value is returned
	@param defVal The default value to be returned. May be null.
    */

    public  <T extends Enum<T>> T getEnum(Class<T> retType, String key, T defVal) throws IllegalInputException  {	    
	Vector<String> v = get(key);
	if (v==null)  return defVal;
	if (v.size()!=1) throwII(key, "Expected exactly 1 data column");
	String x = v.get(0);

	try {
	    return Enum.valueOf(retType, x);
	} catch (Exception ex) {
	    throwII(key, "Invalid value: "+x);
	    return null; // just for the compiler not to complain
	}

    }


    
    public class MyUniform extends sim.util.distribution.Uniform {
	public MyUniform(double min, double max, MersenneTwisterFast randomGenerator) {
	    super(min,max,randomGenerator);
	}
	public double getMin() { return min;}
	public double getMax() { return max;}
	public double computeMean() {
	    return  (getMin() + getMax())/2;
	}	
    }

    public class MyTriangular extends sim.util.distribution.Triangular {
	double _min, _max, _mode;
	public MyTriangular(double min, double mode, double max, MersenneTwisterFast randomGenerator) {
	    super(min,mode,max,randomGenerator);
	    _min = min;
	    _max = max;
	    _mode = mode;
	}
	public double getMin() { return _min;}
	public double getMode() { return _mode;}
	public double getMax() { return _max;}
	public double computeMean() {
	    double mean = (getMin() + getMax())/2;
	    if (Math.abs (getMode()-mean) > 1e-6) throw new IllegalArgumentException("No formula for skewed triangular distribution");
	    return mean;
	}
    }

    /**  binary search for the median of the faultyPortionDistribution */
    private static double findMedian(AbstractDistribution dis) {

	//AbstractContinousDistribution u = (AbstractContinousDistribution)dis;
	Uniform u = (Uniform)dis;
	
	final double eps = 1e-6;
	
	double a=0, b=1, c;
	if (u.cdf(a)>0.5 ||
	    u.cdf(b)<0.5) throw new IllegalArgumentException("Bad cdf for " + dis);
	do {
	    c = (a+b) /2;
	    double val = u.cdf(c);
	    if (val == 0.5) return c;
	    else if (val < 0.5) a = c;
	    else b = c;
	} while(b-a > eps);
	return c;
    }


    
    public static double computeMean(AbstractDistribution dis) {
	if (dis instanceof ParaSet.MyUniform) {
	    MyUniform u = (MyUniform)dis;
	    return  u.computeMean();
	} else if (dis instanceof MyTriangular) {
	    MyTriangular u = (MyTriangular)dis;
	    return  u.computeMean();
	} else {
	    // FIXME: if the distribution is not symmetric, the mean
	    // is not the same as the median
	    //mean =  findMedian();
	    throw new IllegalArgumentException("No formula for mean for distribution=" + dis);
	}
    }
    
    
    /** Creates a random distribution described by the parameters
	in the specified line of this para set.
	@param offset Shift the distribution to the right by this much. Normally 0, this value can be non-zero when modeling disruptions.     
	@return The distribution, or null if one isn't found. (This is handy for some Pools)
    */
    public AbstractDistribution getDistribution(String key,
						MersenneTwisterFast random,
						double offset
						) throws IllegalInputException {
	Vector<String> v = get(key);	
	if (v==null)  return null; // throwII(key, "Missing");  //
	if (v.size()<1) throwII(key, "No data in the row");
	if (v.get(0).equals("Binomial")) {
	    if (offset!=0) throw new IllegalInputException("Cannot apply non-zero offset ("+offset+") to a binomial distribution");
	    Vector<Double> p = parseDoubleParams(key, v, 1, 2);
	    return new Binomial((int)Math.round(p.get(0)),p.get(1), random);
	} else if (v.get(0).equals("Uniform")) {
	    Vector<Double> p = parseDoubleParams(key, v, 1, 2);
	    return new MyUniform(p.get(0)+offset, p.get(1)+offset, random);
	} else if (v.get(0).equals("Normal")) {
	    Vector<Double> p = parseDoubleParams(key, v, 1, 2);
	    return new Normal(p.get(0)+offset,p.get(1), random);
	} else if (v.get(0).equals("Triangular")) {
	    Vector<Double> p = parseDoubleParams(key, v, 1, 3);
	    return new MyTriangular(p.get(0)+offset,p.get(1)+offset, p.get(2)+offset, random);
	} else if (v.get(0).equals("EmpiricalWalker")) {
	    double[] pdf = new double[v.size()-1];
	    for(int j=1; j<v.size(); j++) {
		pdf[j-1] = Double.parseDouble( v.get(j));
	    }
	    return new EmpiricalWalker(pdf, Empirical.NO_INTERPOLATION,
				       random);

	} else {
	    throwII(key, "Random distribution type not supported: " +v.get(0));
	    return null;
	}
    }

      public AbstractDistribution getDistribution(String key,
						  MersenneTwisterFast random) throws IllegalInputException {
	  return  getDistribution(key, random, 0);
      }

    private Vector<Double> parseDoubleParams(String key,Vector<String> v, int startPos, int n)  throws IllegalInputException{
	if (v.size()!=startPos+n) throwII(key, "Expected exactly 3 data column");

	Vector<Double> p = new Vector<>();

	for(int j=0; j<n; j++) {
	    String s = v.get(startPos+j);
	    p.add(parseDoubleEx(key,s));
	}
	return p;
    }

    
    //public class ParaSet extends HashMap<String, Vector<String>>

    /** Given this set is based on all config lines that start with
<tt>
name,...
</tt>
, exract all lines that start with 
<tt>
name,subName,...
</tt>
    */
    /*
    ParaSet getSubset(String subName) {
	ParaSet child = new ParaSet(getName() + "." + subName);
///// .....	
    }
    */
}
