package edu.rutgers.pharma3;

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
    
    public static String getName() { return "Pharma3 Demo"; }

    public void start()        {
        super.start();      
    }
    
    public void init(Controller c)        {
	super.init(c);
    }

    public static void main(String[] argv)   throws IOException, IllegalInputException    {

	argv = Demo.processArgv(argv);
		
        DemoWithUI tutorial2 = new DemoWithUI();
        sim.display.Console c = new sim.display.Console(tutorial2);
        c.setVisible(true);
    }
    
    public void load(SimState state)        {
        super.load(state);
    }

    public Object getSimulationInspectedObject() { return state; }  // non-volatile
}
    
