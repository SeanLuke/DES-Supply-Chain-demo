package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

import edu.rutgers.util.*;

/** An auxiliary tool that's used to report something daily (or whenever it happens).

    <p>
    A Charter object can be created in the constructor of a Named object. A call to Charter.print() then should be placed in that object's step() method (or somewhere, if you want to write data lines on some other schedule). Each print() call will write a line of data.
 */
class Charter {
    private PrintWriter w=null;
    /** The object which will print its data thru this Charter */
    private Named c;
    /** This thing will give us time stamps */
    private Schedule sch;
    
    /** This is used so that we can close all files at  the end of simulation */
    private static Set<Charter> allCharters = new HashSet<>();
    
    private static File dir = new File(".");

    /** @param _dir Directory into which all files will be written.
	Passing null turns off charting. */
    public static void setDir(File _dir) {
	dir = _dir;
    }
    
    public Charter(Schedule schedule,
	    Named _c) throws IOException {
	c = _c;
	
	sch = schedule;

	if (dir==null) {
	    if (Demo.verbose) System.out.println("Charting turned off");
	    return;
	}
	if (!dir.exists()) dir.mkdirs();

	File f = new File(dir, c.getName() + ".csv");
	w = new PrintWriter(new FileWriter(f));
	allCharters.add(this);
    }

    public void printHeader(String... names) {
	if (w==null) return;
	w.println( "#time,"+		   String.join(",",   names));
    }


    /** This should be called from c.step()
	@param values The value(s) to print. Can also take double[]  
    */
    public void print(double... values) {
	if (w==null) return;
	w.println( ""+sch.getTime()+ ","+
		   Util.joinNonBlank(",",   values)); // c.getValue());
	//w.flush();
    }

    /** Closes the file associated with this charter object, if it's still open open
	@return true if the closing, in fact, needed to be done
     */
    synchronized boolean close() {
	if (w==null) {
	    return false;
	} else {
	    w.flush();
	    w.close();
	    w=null;
	    return true;
	}
    }

    public void finalize() {
	close();
    }

    /** Call ths from SimState.finish(), to ensure all files are closed. */
    public static void closeAll() {
	int n= 0;
	for(Charter x: allCharters) {
	    if (x.close())  n++;
	}
	allCharters.clear();
	if (!Demo.quiet) System.out.println("Closed all " + n+ " logs");
    }
    
}
