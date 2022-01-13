package edu.rutgers.pharma2;

import sim.engine.*;
import sim.display.*;
import sim.portrayal.grid.*;
import java.awt.*;
import javax.swing.*;

public class DemoWithUI extends GUIState
    {
    public Display2D display;
    public JFrame displayFrame;

    public DemoWithUI() { super(new Demo(System.currentTimeMillis())); }
    
    public DemoWithUI(SimState state) { super(state); }
    
    public static String getName() { return "Pharma2 demo"; }

    public void start()
        {
        super.start();      
        }
    
    public void init(Controller c)
        {
        super.init(c);
        }

    public static void main(String[] args)
        {
        DemoWithUI tutorial2 = new DemoWithUI();
        Console c = new Console(tutorial2);
        c.setVisible(true);
        }
    
    public void load(SimState state)
        {
        super.load(state);
        }

    public Object getSimulationInspectedObject() { return state; }  // non-volatile
    }
    
