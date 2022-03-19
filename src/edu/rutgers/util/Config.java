package edu.rutgers.util;

import java.io.*;
import java.util.*;

import ec.util.MersenneTwisterFast;
import sim.util.distribution.*;

public class Config extends HashMap<String,ParaSet> {
    
    /** Reads a CSV file into a table of ParaSet objects, one object per 
	supply chain element. */
    public static Config readConfig(File f) throws IOException, IllegalInputException  {
	Config h = new Config();
	//HashMap<String,ParaSet> h = new HashMap<>();
	//	    File f= new File(base, "colors.csv");
	if (!f.exists()) throw new IOException("File does not exist: " + f);
	if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	CsvData csv = new CsvData(f, true, false, null);
	for(CsvData.LineEntry _e: csv.entries) {
	    CsvData.BasicLineEntry e = (CsvData.BasicLineEntry)_e;
	    String name = e.getKey();
	    ParaSet para = h.get(name);
	    if (para==null) 	h.put(name, para=new ParaSet(name));	    
	    para.add(e);
	}
	
	return h;
    }

    /** A wrapper around get(), which throws an exception (instead of
	returning null) if the value is not found */
    public ParaSet get2(String name) throws  IllegalInputException {
	ParaSet para = get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	return para;
    }
    
}
