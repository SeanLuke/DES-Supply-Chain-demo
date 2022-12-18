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
	    Ben's "forklift model", rather than "containter ship" model,
	    we are not actually using this disruption type in SC-1.	    
	*/
	Delay,
	/** All shipments sent during a certain time period (given by
	   "magnitued") will be lost in transit.
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
    };

    /** A single disruption event. A disruption may take place of a momentary
	even (Depletion), a modification of the production process in effect
	for one day (Adulteration), or a phenomenon that last for several
	days (Halt).

	
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
	Disruption(Type _type, String _unitName, double _time, double _magnitude) {
	    type = _type;
	    unitName = _unitName;
	    time = _time;
	    magnitude = _magnitude;
	}

	public String toString() {
	    return "At " + time + ", " + type + "@" + unitName + ", magnitude=" + magnitude;
	}
   
    }

    /** The schedule of disruption events */
    private Vector<Disruption> data = new Vector<>();

    /** Are there any disruptions of the specified king scheduled for today?
	For simplicity, both the current time and the scheduled disruption
	time are rounded to integers; i.e. we have 1-day granularity.
	@param type Matching disruptions have to be of this type
	@param unitName Matching disruptions have to affect this unit
	@param time Matching disruptions have to happen during this day.
     */
    public Vector<Disruption> hasToday(Type type, String unitName, double time) {
	Vector<Disruption> v = new Vector<>();
	for(Disruption d: data) {
	    if (d.type == type &&
		Math.round(d.time) == Math.round(time) &&
		d.unitName.equals(unitName)		) v.add(d);

	}
	//System.out.println("Disruptions.hasToday("  +type+"@" +unitName + ", at " + time +") gives " + v.size());
	return v;
    }

    /** Adds a Disruption event to this Disruptions object. This can be
	used e.g. by an optimization program as it assembles a disruption
	scenario to test in a simulation run.
     */
    public void add(Type _type, String _unitName, double _time, double _magnitude) {
	data.add(new Disruption(_type, _unitName,  _time, _magnitude));
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
       
    /** Reads a CSV file with a list of disruption events (one disruption per line). Line format:
<pre>
time,unit,type,amount
</pre>

@param f The disruption scenario CSV file to read
@return The new disruption scenario based on the CSV file
     */
    public static Disruptions readList(File f) throws IOException, IllegalInputException  {
	Disruptions h = new Disruptions();
	h.readFrom = f;

	if (!f.exists()) throw new IOException("File does not exist: " + f);
	if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	CsvData csv = new CsvData(f, true, false, null);
	
	int j = 0;
	for(CsvData.LineEntry _e: csv.entries) {
	    j++;
	    CsvData.BasicLineEntry e = (CsvData.BasicLineEntry)_e;
	    if (e.nCol()!=4) throw new  IllegalInputException("Illegal number of columns in file "+h.readFrom+": " + e.nCol() +". Expected 4. Data line no. " + j);
	    Double time = e.getColDouble(0);
	    String unit = e.getCol(1);
	    String typeString = e.getCol(2);
	    Double magnitude = e.getColDouble(3);
	    if (time==null || magnitude==null || unit==null || unit.length()==0) throw new IllegalInputException("Illegal data in data line no. " + j + ". Data=" + e);
	    Type type;
	    try {
		type =  Enum.valueOf(Type.class,  typeString);
	    } catch(Exception ex) {
		throw new IllegalInputException("Invalid disruption type ("+ typeString+") in file "+h.readFrom+", data line no. " + j);
	    }	    
	    h.add(type, unit, time, magnitude);
	}
	
	return h;
    }

}
