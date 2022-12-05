package  edu.rutgers.supply;

import java.io.*;
import java.util.*;

import edu.rutgers.util.*;


/** An instance of this class contains a "disruption schedule", i.e. a
    list of dsisruptive events. Each element of the list comes with the
    time and magnitude. 
*/

public class Disruptions {

    public enum Type {
	Delay, // not needed any more
	ShipmentLoss,
	Adulteration,
	Depletion,
	Halt,
    };

    /** A single disruption event.
	FIXME: Ought to replace "public" fields with getter methods
     */
    public static class Disruption {
	Type type;
	String unitName;
	/** On the usual simulation clock, i.e. in days */
	double time;
	public double magnitude;
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

    /** The schedule of disruption event */
    Vector<Disruption> data = new Vector<>();

    /** Are there any disruptions of this type scheduled for today?
	For simplicity, both the current time and the scheduled disruption
	time are rounded to integers; i.e. we have 1-day granularity.
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

    void add(Type _type, String _unitName, double _time, double _magnitude) {
	data.add(new Disruption(_type, _unitName,  _time, _magnitude));
    }

    public String toString() {
	return "Scheduled " + data.size() + " disruptions:\n" +
	    Util.joinNonBlank("\n", data);
    }


    File readFrom = null;
       
    /** Reads a CSV file with a list of disruption events (one disruption per line). Line format:

time,unit,type,amount
     */
    public static Disruptions readList(File f) throws IOException, IllegalInputException  {
	Disruptions h = new Disruptions();
	h.readFrom = f;
	//HashMap<String,ParaSet> h = new HashMap<>();
	//	    File f= new File(base, "colors.csv");
	if (!f.exists()) throw new IOException("File does not exist: " + f);
	if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	CsvData csv = new CsvData(f, true, false, null);
	
	int j = 0;
	for(CsvData.LineEntry _e: csv.entries) {
	    j++;
	    CsvData.BasicLineEntry e = (CsvData.BasicLineEntry)_e;
	    if (e.nCol()!=4) throw new  IllegalInputException("Illegal number of columns: " + e.nCol() +". Expected 4. Data line no. " + j);
	    Double time = e.getColDouble(0);
	    String unit = e.getCol(1);
	    String typeString = e.getCol(2);
	    Double magnitude = e.getColDouble(3);
	    if (time==null || magnitude==null || unit==null || unit.length()==0) throw new IllegalInputException("Illegal data in data line no. " + j + ". Data=" + e);
	    Type type;
	    try {
		type =  Enum.valueOf(Type.class,  typeString);
	    } catch(Exception ex) {
		throw new IllegalInputException("Invalid disruption type ("+ typeString+") in data line no. " + j);
	    }	    
	    h.add(type, unit, time, magnitude);
	}
	
	return h;
    }

}
