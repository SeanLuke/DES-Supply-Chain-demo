package edu.rutgers.pharma2;

import sim.engine.*;
import sim.display.*;
import sim.portrayal.grid.*;

import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.util.*;

import edu.rutgers.util.*;

public class DemoWithUI extends GUIState    {
    public Display2D display;
    public JFrame displayFrame;

    public DemoWithUI() { super(new Demo(System.currentTimeMillis())); }
    
    public DemoWithUI(SimState state) { super(state); }
    
    public static String getName() { return "Pharma2 demo"; }

    public void start()        {
        super.start();      
    }
    
    public void init(Controller c)        {
	super.init(c);
    }

    public static void main(String[] argv)   throws IOException, IllegalInputException    {

	String confPath = "config/pharma2.csv";

	Vector<String> va = new Vector<String>();
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    if (a.equals("-verbose")) {
		Demo.verbose = true;
	    } else if (a.equals("-config") && j+1<argv.length) {
		confPath= argv[++j];
	    } else {
		va.add(a);
	    }
	}

	argv = va.toArray(new String[0]);
	
	File f= new File(confPath);
	Demo.config  = Config.readConfig(f);
	
        DemoWithUI tutorial2 = new DemoWithUI();
        sim.display.Console c = new sim.display.Console(tutorial2);
        c.setVisible(true);
    }
    
    public void load(SimState state)        {
        super.load(state);
    }

    public Object getSimulationInspectedObject() { return state; }  // non-volatile
}
    
