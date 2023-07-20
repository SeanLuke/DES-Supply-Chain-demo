package  edu.rutgers.supply;

import java.io.*;
import java.util.*;

import edu.rutgers.util.Util;
import edu.rutgers.util.CsvData;
import edu.rutgers.util.IllegalInputException;


/** An instance of this class contains a "disruption schedule", that is a
    list of dsisruptive events. Each element of the list is represented
    by a Disruption object that specifies the event's time, type,
    location, and magnitude.

    <p>The Disruptions object is typically created at the beginning of
    the simulation, when it is read from the "disrupion scenario" file
    (with 1 line of the CSV file corresponding to 1 Disruption
    object), or is supplied by an optimization program that wants to
    find out how the supplied chain will work under a particular
    disruption scenario.

    <p>During the simulation process, each supply chain element that
    may be disrupted, watches out for disruptions that may affect
    it. To do that, when the supply chain element is stepped daily, it
    checks (with Disruptions.hasToday()) whether the Disruptions
    object contains any Disruption instances that affect this element
    on this calendar day; if such a Disruption instances are found,
    the supply chain element modifies its behavior for this day (and,
    possibly, for a certain future period) appropriately. E.g. it may
    destroy some amount of a product in its storage facility, or it may
    mark itself as "out of service" for a specified amount of time.
*/

public class Disruptions {

    /** Types of disruptions. The list is loosely based on Abhisekh's 21-point
        "disruption menu" from the summer of 2022.
     */
    public enum Type {
	/** This was introduced to code for specified-length delays of
	    all shipments sent on a given date; but since in SC-1 we're using
	    Ben's "forklift model", rather than the "containter ship" model,
	    we are not actually using this disruption type in SC-1.	    
	*/
	Delay,
	/** All shipments sent during a certain time period (given by
	   "magnitude" in SC-1/SC-2, "duratiokn" since SC-3) will be lost in transit.
	 */
	ShipmentLoss,
	/** The quality of goods produced on this day will be lower
	    than normal. The decrease in quality is described by the
	    magnitude field. */
	Adulteration,
	/** A one-time destruction of a certain amount (given by magnitude,
	    with a scale factor) of a certain product at a certain
	    storage facility */
	Depletion,
	/** The stoppage of a certain production facility; it will continue
	    for the number of days specified by magnitude. */
	Halt,
	/** The safety stock stops sending replenishment requests for
	    a specified number of days. (SC-2) */
	DisableTrackingSafetyStock,
	/** Orders get lost during 
	    a specified number of days. (SC-2) */
	StopInfoFlow,
	/** Increases the production delay by a specified factor (typically,
	    greater than 1.0 if one wants to slow down things)
	*/
	ProdDelayFactor,
	/** Increases the testing delay by a specified factor (typically,
	    greater than 1.0 if one wants to slow down things)
	*/
	TransDelayFactor,
	/** Increases the transportation delay by a specified factor (typically,
	    greater than 1.0 if one wants to slow down things)
	*/
	QaDelayFactor,
	/** This is not a disruption at all, but a command to turn
	    on a manually controlled production node for a specified
	    number of days.
	*/
	On;

	/** Disruptions which in SC-1 and/or SC-2 contained the duration value
	    in the magnitude field. */
	public boolean oldDurational() {
	    return (this == Halt ||
		   this == DisableTrackingSafetyStock ||
		   this == StopInfoFlow ||
		   this == On);
	}
    };

    /** This is set to true (default) if we want this class' functionality
	to be compatible with SC-1/SC-2 expectations; set to true in SC-3.
    */
    private static boolean sc2BackwardCompatible=true;
    
    public static void setSc2BackwardCompatible(boolean x) {
	sc2BackwardCompatible=x;
    }

    public static boolean getSc2BackwardCompatible() {
	return sc2BackwardCompatible;
    }


    /** A single disruption event. A disruption may take place of a
	momentary even (Depletion) which is thought to happen at the
	beginning of the day, or a modification of the production
	process which is in effect for one or several days (Adulteration, Halt).

	<p>In SC-1 and SC-2 there was a distinction between events
	that were ineffect for 1 day (Adulteration), and those that
	would last for for several days (Halt). This is because there
	was just one numerical field ("magnitude" in the Java class,
	aka "amount" in CSV files), which had the "intensity"
	semantics for the former and the "duration" semantics for the
	latter.  Since SC-3, this distinction does not exist anymore,
	because each Disruption instance has a dedicated "duration"
	field for the duration.

	<p>
	FIXME: Ought to replace "public" fields with getter methods
     */
    public static class Disruption {
	/** What happens? (Shipment delay, destruction of stock, production stoppage...)
	 */
	final Type type;
	/** Where does the disruption happen? The convention is that
	    this value may store the name of a supply chain element
	    (which implements the Named interface), or the name of the
	    product. Ultimately, for the disruption to "play" in the simulation
	    process, this name should match the name with which a simulation
	    unit will be looking for the disruption using a method such
	    as hasToday()
	*/
	final String unitName;
	/** When does the disruption happen? On the usual simulation clock, i.e. in days */
	final double time;
	/** The magnitude of the disruption. This is interpreted by the
	    supply chain element that is affected by disruption; the meaning
	    therefore depends on the type and location of the disruption.
	    For example, for a Depletion it refers to the amount of destroyed
	    product (with a scale factor); for a Halt, to the number of days
	    the stoppage will last for.
	 */
	final public double magnitude;
	/** For how many day the disruption lasts. The default value is 1  */
	final public double duration;

	public double end() {
	    return time + duration;
	}
	
	Disruption(Type _type, String _unitName, double _time, double _magnitude) {
	    this(_type, _unitName, _time, 1, _magnitude);
	}

	Disruption(Type _type, String _unitName, double _time, double _duration, double _magnitude) {
	    type = _type;
	    unitName = _unitName;
	    time = _time;

	    // For backward compatibility with SC-1 and SC-2, where the duration
	    // value was provided via the magnitude field
	    if (sc2BackwardCompatible && type.oldDurational()) {
		if (_magnitude < 1) throw new IllegalArgumentException("Magnitude<1 is not supported for type=" + type);
		else if (_magnitude > 1 && _duration==1) {	       
		    // So that SC-3 would still work with old-style scenario file
		    _duration = _magnitude;
		} else if (_magnitude== 1 && _duration>1) {
		    // So that SC-1 and SC-2 apps still would run with the SC-3 disrupton code
		    _magnitude=_duration;
		} else {
		    throw new IllegalArgumentException("Magnitude>1 is not supported for type=" + type);
		}
	    }
	    
	    magnitude = _magnitude;
	    duration = _duration;


	    if (duration < 1.0) throw new IllegalArgumentException("Illegal duration="+duration+" in disruption ("+this+"). Each disruption must be at least 1 day long");


	}

	public String toString() { 
	    return "At " + time + ", " + type + "@" + unitName + ", magnitude=" + magnitude + ", lasts " + duration + " days";
	}

	/** #time,unit,type,duration,amount  */
	public String toCsv() {
	    Object [] v = {time, unitName, type, duration, magnitude};
	    return Util.join(",", v);
	}
   
    }

    /** The schedule of disruption events */
    private Vector<Disruption> data = new Vector<>();

    /** Are there any disruptions of the specified king scheduled for today?

	<p>In SC-2 and before, 
	for simplicity, both the current time and the scheduled disruption
	time were rounded to integers; i.e. we had 1-day granularity
	(Math.round(d.time) == Math.round(time)). In SC-3, when disruptions may
	have arbitrary duration, the disruption is considered to be "on" a given
	day if the beginning of the day falls within the range [t, t+duration).

	@param type Matching disruptions have to be of this type
	@param unitName Matching disruptions have to affect this unit
	@param time Matching disruptions have to happen during this day. This is the beginning-of-the day timepoint, i.e. an integer.
     */
    public Vector<Disruption> hasToday(Type type, String unitName, double time) {
	Vector<Disruption> v = new Vector<>();
	for(Disruption d: data) {
	    if (d.type == type &&
		time >= d.time && time < d.end() &&
		d.unitName.equals(unitName)		) v.add(d);

	}
	//System.out.println("Disruptions.hasToday("  +type+"@" +unitName + ", at " + time +") gives " + v.size());
	return v;
    }

    /** Adds a Disruption event to this Disruptions object. This can be
	used e.g. by an optimization program as it assembles a disruption
	scenario to test in a simulation run. This is the standard
	method since SC-3.
     */
    public void add(Type _type, String _unitName, double _time, double _duration, double _magnitude) {
	data.add(new Disruption(_type, _unitName,  _time, _duration, _magnitude));
    }
    
    /** Exists for compatibility with SC-1 and SC-2 */
    public void add(Type _type, String _unitName, double _time, double _magnitude) {
    	add(_type, _unitName,  _time, 1.0, _magnitude);
    }

    public String toString() {
	return "Scheduled " + data.size() + " disruptions:\n" +
	    Util.joinNonBlank("\n", data);
    }


    /** The file, if any, from which the disruption scenario has been
	read.  This is mostly kept for use in error messages. The
	value is null if the Disruptions object was created on-the-fly
	(e.g. in an optimization program), without a scenario file.
     */
    private File readFrom = null;

    final static String header0= "time,unit,type,amount",
	header1= "time,unit,type,duration,amount";

    
    /** Reads a CSV file with a list of disruption events (one disruption per line). Line format:
<pre>
#time,unit,type,duration,amount
time,unit,type,amount
</pre>
or
<pre>
timeStart-timeEnd,unit,type,amount
</pre>
In the latter case, the disruption repears every day, with the same type and magnitude, from timeStart to timeEnd, inclusively.  E.g. 10-20 will have the disruption repeat 11 times, from day 10 thru day 20.


<p>Since SC-3, the default format is 
<pre>
#time,unit,type,duration,amount
time,unit,type,duration,amount
</pre>

<p>
The method determines the format based on the header line, i.e. the first line of the file, staring with the '#' character.

@param f The disruption scenario CSV file to read
@return The new disruption scenario based on the CSV file
     */
    public static Disruptions readList(File f) throws IOException, IllegalInputException  {
	Disruptions h = new Disruptions();
	h.readFrom = f;

	if (!f.exists()) throw new IOException("File does not exist: " + f);
	if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	CsvData csv = new CsvData(f, true, false, null);
	
	int j = 0, lineNo=0;
	boolean hasDuration=true;
	for(CsvData.LineEntry _e: csv.entries) {
	    if (lineNo==0 && _e instanceof CsvData.CommentEntry) {
		CsvData.CommentEntry header = (CsvData.CommentEntry)_e;

		if (header.text.equals(header0)) hasDuration = false;
		else if (header.text.equals(header1)) hasDuration = true;
		else {
		    String msg="Don't know how to interpret the header line\n#" + header.text +
			"\nThe only supported formats are\n" + header0 +
			"\nand\n" + header1;
		    throw new IllegalArgumentException(msg);
		}
	    }
	    final int nExpect = hasDuration? 5:4;
	    j++;
	    CsvData.BasicLineEntry e = (CsvData.BasicLineEntry)_e;
	    if (e.nCol()!=nExpect) throw new  IllegalInputException("Illegal number of columns in file "+h.readFrom+": " + e.nCol() +". Expected "+nExpect+". Data line no. " + j);
	    Double[] tt = {null, null};

	    //	    Double time = e.getColDouble(0);

	    String ss[] = e.getCol(0).split("-");
	    if (ss.length<1 || ss.length>2) throw new  IllegalInputException("Illegal time format ("+e.getCol(0)+")number of columns in file "+h.readFrom+". Data line no. " + j);
	    for(int k=0; k<ss.length; k++) {
		try {
		    tt[k] = Double.parseDouble(ss[k]);
		} catch(Exception ex) {}
	    }
	    int k=1;
	    String unit = e.getCol(k++);
	    String typeString = e.getCol(k++);
	    Double duration = hasDuration? e.getColDouble(k++): 1;
	    Double magnitude = e.getColDouble(k++);
	    if (tt[0]==null || magnitude==null || unit==null || unit.length()==0) throw new IllegalInputException("Illegal data in data line no. " + j + ". Data=" + e);
	    Type type;
	    try {
		type =  Enum.valueOf(Type.class,  typeString);
	    } catch(Exception ex) {
		throw new IllegalInputException("Invalid disruption type ("+ typeString+") in file "+h.readFrom+", data line no. " + j);
	    }
	    
	    h.add(type, unit, tt[0], duration, magnitude);
	    if (tt[1]!=null) {
		for(double time=tt[0]+1; time<=tt[1]; time += 1) {
		    h.add(type, unit, time, duration, magnitude);
		}
	    }
	}
	
	return h;
    }

    /** Writes out the scenario in the same CSV file format that can
	be read in. */
    public String toCsv() {
	Vector<String> v = new Vector<>();
	v.add("#" + header1);
	for(Disruption d: data) {
	    v.add( d.toCsv());
	}
	return String.join("\n", v);
    }
    
}
