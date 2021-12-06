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
*/

public class ParaSet extends HashMap<String, Vector<String>> {

    /** The name of the ParaSet, i.e. the name of the supply set element
	whose parameters this ParaSet contains.
     */
    final public String name;

    ParaSet(String _name) {
	name = _name;
    }

    
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
	Vector<String> v = get(key);
	if (v==null)  throwII(key, "Missing");
	if (v.size()!=1) throwII(key, "Expected exactly 1 data column");
	return v.get(0);
	
    }



    public double getDouble(String key) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null)  throwII(key, "Missing");
	if (v.size()!=1) throwII(key, "Expected exactly 1 data column");
	String s = v.get(0);
	try {
	    return Double.parseDouble(s);
	} catch(Exception ex) {
	    throwII(key, "Cannot parse as a real number: " + s);
	    return 0; // never reached
	}
	
    }

    public double [] getDoubles(String key) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null)  throwII(key, "Missing");
	if (v.size()<1) throwII(key, "Expected at least 1 data column");
	double[] q = new double[v.size()];
	for(int j=0; j<q.length; j++) {
	    String s = v.get(j);
	    try {
		q[j] = Double.parseDouble(s);
	    } catch(Exception ex) {
		throwII(key, "Cannot parse as a real number: " + s);
	    }
	}
	return q;	
    }
    
    public long getLong(String key) throws IllegalInputException {
	Vector<String> v = get(key);
	if (v==null)  throwII(key, "Missing");
	if (v.size()!=1) throwII(key, "Expected exactlt 1 data column");
	String s = v.get(0);
	try {
	    return Long.parseLong(s);
	} catch(Exception ex) {
	    throwII(key, "Cannot parse as a long int: " + s);
	    return 0; // never reached
	}
	
    }

    /** Creates a random distribution described by the parameters
	in the specified line of this para set */
    public AbstractDistribution getDistribution(String key,
						MersenneTwisterFast random) throws IllegalInputException {
	Vector<String> v = get(key);	
	if (v==null)  throwII(key, "Missing");
	if (v.size()!=3) throwII(key, "Expected exactly 3 data column");
	if (v.get(0).equals("Uniform")) {
	    Vector<Double> p = new Vector<>();
	    for(int j=0; j<2; j++) {
		String s = v.get(1+j);
		try {
		    p.add(new Double(s));
		} catch(Exception ex) {
		    throwII(key, "Cannot parse as a real number: " + s);
		}
	    }
	    return new Uniform(p.get(0),p.get(1), random);
	} else {
	    throwII(key, "Random distribution type not supported: " +v.get(0));
	    return null;
	}
    }

    
}
