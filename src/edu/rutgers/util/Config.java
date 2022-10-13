package edu.rutgers.util;

import java.io.*;
import java.util.*;

import ec.util.MersenneTwisterFast;
import sim.util.distribution.*;

/** The top-level structure for all configuration parameters read from a config
    file. 
*/
public class Config extends HashMap<String,ParaSet> {
    /** Just for information purposes */
    public File readFrom = null;
    
    /** Reads a CSV file into a table of ParaSet objects, one object per 
	supply chain element. */
    public static Config readConfig(File f) throws IOException, IllegalInputException  {
	Config h = new Config();
	h.readFrom = f;
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
	
	ParaSet defaultPara = h.get("default");
	if (defaultPara!=null) {
	    for(ParaSet para: h.values()) {
		if (para!= defaultPara && para.getParent()==null) para.setParent( defaultPara);
	    }
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

    /** Adds a new parameter (and, if necessary, a new parameter set
	for it), to this Config. If the named parameter already
	exists, changes its value.
	@param name The para set name
	@param key the name of the new parameter in that para set
	@param val the value for the new parameter
    */
    public void addNewParameter(String name, String key, String val){
    
    	ParaSet para = this.get(name);
    	
	if (para==null){
	    this.put(name, para=new ParaSet(name));

	    // Attach the default para set as the fallback, for
	    // "common" parameters such as "batch"
	    ParaSet defaultPara = get("default");
	    if (defaultPara!=null) {
		para.setParent( defaultPara);
	    }
	}
		
	Vector<String> v  = new Vector<>();
	v.add( val );
	para.put( key, v);    
	
    
    }
    
}
