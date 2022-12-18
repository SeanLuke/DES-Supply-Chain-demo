package  edu.rutgers.supply;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

import edu.rutgers.util.Util;

/** An auxiliary tool that's used to report something daily (or whenever it happens).

    <p>
    A Charter object can be created in the constructor of a Named object. A call to Charter.print() then should be placed in that object's step() method (or somewhere, if you want to write data lines on some other schedule). Each print() call will write a line of data.


    <p>In a typical usage of this class, the caller will:
    <ol>
    <li>Set the output directory with setDir()
    <li>Create as many Charter objects as needed. Typically, you'll create
    one Charter object for each simulation element (e.g. a production node)
    whose timeseries you wanted to chart. This Charter will print
    as many columns as you have variables associated with that object that
    you want to chart (e.g. the daily production amount, the daily backlog,
    and the daily discard amount).
    <li>For each Charter object, call printHeader() with the column names.
    <li>During the simulation, each simulation object that has a Charter
    will call print() on that object at each time step (or however often
    you want the data to be saved).
    <li>Once the simulation has finished, call Charter.closeAll().
    </ol>
    
    
    <p> The resulting CSV file can then be viualized with tools such as gnuplot.
For example:
<pte>
    set datafile separator ','

#-- to make sure the tic labels on the Y-axis fit into the PNG image
set lmargin 8
set grid mxtics mytics

set term qt 0
set title 'Hospital/Pharmacy Pool'
plot 'HospitalPool.csv'  using ($1):($2)  with lines title 'HP.Stock', \
'HospitalPool.csv'  using ($1):($3)  with impulses title 'HP.Ordered', \
'HospitalPool.csv'  using  ($1):($4)  with lines title 'HP.Received'
set term png size 800,600
set out 'HospitalPool.png'
replot
</pre>

For more examples, see charts.gnu.

 */
public class Charter {
    private PrintWriter w=null;
    /** The object which will print its data thru this Charter */
    private Named c;
    /** This thing will give us time stamps */
    private Schedule sch;
    
    /** This is used so that we can close all files at  the end of simulation */
    private static Set<Charter> allCharters = new HashSet<>();

    /** The directory into which chart files are written */
    private static File dir = new File(".");

    /** Sets the output directory for chart files. It will be used by all
	Charter objects to be created. This method should be called
	before creating any Charter objects. 
       @param _dir Directory into which all files will be written.
       Passing null turns off charting. */
    public static void setDir(File _dir) {
	dir = _dir;
    }

    /** Creates a Charter object.
	@param schedule This will be used to provide the time stamps in all
	lines of the CSV file to be written.
	@param _c The name of this object will be used to set the name of the output file.
     */
    public Charter(Schedule schedule,  Named _c) throws IOException {
	c = _c;
	
	sch = schedule;

	if (dir==null) {
	    //if (Demo.verbose) System.out.println("Charting turned off");
	    return;
	}
	if (!dir.exists()) {
	    if (!dir.mkdirs()) throw new IOException("Failed to create dir=" + dir);
	}

	File f = new File(dir, c.getName() + ".csv");
	w = new PrintWriter(new FileWriter(f));
	allCharters.add(this);
    }

    /** Prints the header of the CSV file. This should be called before
	writing any lines of data.
      
	@param names The names of all the columns (other than the first column, "time")
	
     */
    public void printHeader(String... names) {
	if (w==null) return;
	w.println( "#time,"+		   String.join(",",   names));
    }


    /** Prints a line of data. This should be called from c.step()
	@param values The value(s) to print (not including the time stamp,
	which will be automatically taken from the Scheduler object).
	Can also take double[]  
    */
    public void print(double... values) {
	if (w==null) return;
	w.println( ""+sch.getTime()+ ","+
		   Util.joinNonBlank(",",   values)); // c.getValue());
	//w.flush();
    }

    /** Closes the CSV file associated with this charter object, if it's still open open
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

    /** Call ths from SimState.finish(), to ensure all files are
	closed, and resources are deallocated. This should be done at the
	end of the simulation.
    */
    public static void closeAll() {
	int n= 0;
	for(Charter x: allCharters) {
	    if (x.close())  n++;
	}
	allCharters.clear();
	//if (!Demo.quiet) System.out.println("Closed all " + n+ " logs");
    }
    
}
