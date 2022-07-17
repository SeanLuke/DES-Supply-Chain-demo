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
    PrintWriter w=null;
    Schedule sch;
    Named c;

    /** This is used so that we can close all files at  the end of simulation */
    static Set<Charter> allCharters = new HashSet<>();
    
    static File dir = new File(".");

    static void setDir(File _dir) {
	dir = _dir;
    }
    
    Charter(Schedule schedule,
	    Named _c) throws IOException {
	c = _c;
	
	sch = schedule;
       	
	File f = new File(dir, c.getName() + ".csv");
	w = new PrintWriter(new FileWriter(f));
	allCharters.add(this);
    }

    /** This should be called from c.step() */
    void print(double value) {
	w.println( ""+sch.getTime()+ ","+ value); // c.getValue());
	//w.flush();
    }

    synchronized void close() {
	if (w!=null) {
	    w.flush();
	    w.close();
	}
	w=null;
    }

    public void finalize() {
	close();
    }

    /** Call ths from SimState.finish(), to ensure all files are closed. */
    public static void closeAll() {
	int n= 0;
	for(Charter x: allCharters) {
	    x.close();
	    n++;
	}
	System.out.println("Closed all " + n+ " logs");
    }
    
}
