package  edu.rutgers.supply;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.des.*;

import edu.rutgers.util.Util;
import edu.rutgers.util.IllegalInputException;


/** Just prints a predefined line of text. This is used as part of a complex report.
*/
public class Filler implements Reporting {

    final String text;

    public Filler(String _text) { text = _text; }
    
    public String report() {
	return text;
    }

}
