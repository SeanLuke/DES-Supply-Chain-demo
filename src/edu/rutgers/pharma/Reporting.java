package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
//import sim.field.continuous.*;
import sim.des.*;

/** An object implementing this interface will be able to tell something about its current getState() */
interface Reporting {
    public String report();
}
