package  edu.rutgers.pharma3;

import java.io.*;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.app.flockers.Flocker;
import sim.des.*;

import ec.util.MersenneTwisterFast;

import edu.rutgers.util.*;
import edu.rutgers.supply.*;
import edu.rutgers.supply.Disruptions.Disruption;
import edu.rutgers.supply.Disruptions.Type;
import sim.portrayal.grid.*;

import sim.portrayal.network.*;
import sim.portrayal.continuous.*;
import sim.display.*;
import sim.portrayal.simple.*;
import sim.portrayal.*;
import javax.swing.*;
import java.awt.Color;
import java.awt.*;
import sim.field.network.*;
import sim.des.portrayal.*;


/** The main class for a  simple pharmaceutical supply chain simulation demo */
public class DemoOptDisruption extends Demo {

    double disruptionBudget;
    
    public DemoOptDisruption(long seed, double disruptionBudget)    {
    
	super(seed);
	
	Demo.quiet = true;
	Demo.verbose = false;
	
	this.disruptionBudget = disruptionBudget;
	
	try {
		mySetUp();
		
		
	} catch (Exception e) {
		e.printStackTrace();
	}
	
	System.out.println("pharma3.DemoOptDisruption()");
	
    }
    



	//setup without commandline parameters, 
	public void mySetUp() throws IOException, IllegalInputException{
		/*
		int time = 0;
	    this.disruptions = new Disruptions();
	    this.disruptions.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "RawMaterialSupplier", time, 400.0);
	    
	    this.disruptions.add(Enum.valueOf(Type.class,  "Adulteration"), "RawMaterialSupplier", time, 400.0);

	    this.disruptions.add(Enum.valueOf(Type.class,  "Depletion"), "RawMaterial", time, 400.0);
	    
	    this.disruptions.add(Enum.valueOf(Type.class,  "Depletion"), "Api", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Halt"), "ApiProduction", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Adulteration"), "ApiProduction", time, 400.0);
	    
	    this.disruptions.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "ExcipientSupplier", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Adulteration"), "ExcipientSupplier", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Depletion"), "Excipient", time, 400.0);
	    
	    this.disruptions.add(Enum.valueOf(Type.class,  "Depletion"), "BulkDrug", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Halt"), "DrugProduction", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Adulteration"), "DrugProduction", time, 400.0);

	    this.disruptions.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "PacMatSupplier", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Adulteration"), "PacMatSupplier", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Depletion"), "PackagingMaterial", time, 400.0);
	    
	    this.disruptions.add(Enum.valueOf(Type.class,  "Depletion"), "Packaging", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Halt"), "Packaging", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Adulteration"), "Packaging", time, 400.0);
	   	    
	    this.disruptions.add(Enum.valueOf(Type.class,  "Depletion"), "Distributor", time, 400.0);
	    this.disruptions.add(Enum.valueOf(Type.class,  "Delay"), "Distributor", time, 400.0);
  	    */

	    //String confPath = "legacy-2.007-safety/pharma3.orig.csv";
	    //String confPath = "../config/pharma3.orig.csv";
	    String confPath = "../config/pharma3.nosafety.csv";

	    String disruptPath = null; //don't need
	    String chartsPath = "charts";


	
	    File f= new File(confPath);
	    this.config  = Config.readConfig(f);


	    // The chart directory
	    File logDir = new File(chartsPath);
	    Charter.setDir(logDir);
    
	}
	
    /** Here, the supply network elements are added to the Demo object */
    public void start(){

	super.start();
    }



    public void	finish() {
    
        super.finish();
        System.out.println(-1.0 * pharmaCompany.getDistributor().getEverReceived());
    
    }




	public java.lang.Class	simulationClass() {
	    return DemoOptDisruption.class;
	}	    
	//public java.lang.reflect.Constructor[]	getConstructors() {
	//    return DemoNoCommandLineArg.class.getConstructors();
	//}

    
    /** Extracts a few command-line options we understand, and leaves
	the rest of them to MASON.
    */
    public static void main(String[] argv) throws IOException, IllegalInputException {

    for (String a : argv) {
    	System.out.println(a);
    }
    	
    //System.out.println(argv.length);
    
    //System.exit(-1);
    
    String[] new_argv = new String[2];
    new_argv[0] = "-until"; //instead of -until
    new_argv[1] = "365"; //opt ignores anyway
    //new_argv[1] = "100000"; //opt ignores anyway

    //new_argv[2] = "-disrupt";
    //new_argv[3] = "../config/dis.A2.csv";

	
	doLoop(DemoOptDisruption.class, new_argv);
	//doLoop(maker, argv);C
	
	System.exit(0);
    }
    
    public double[] assess(int numObjectives)
    {
    	
    double[] assessment = new double[numObjectives];
    
    assessment[0] = -1.0 * pharmaCompany.getDistributor().getEverReceived();

    //assessment[0] = disruptions.data.elementAt(0).magnitude;

    
    return assessment;
    } 
    
    //Raj CMAES methods
    //The optimizer uses this to input 
    public void setOptimizationParameters(double[] parameterValues) {
    	
    	//System.out.println("rabbit");
    	//System.exit(-1);
    	
    	boolean apply_weights = false;
    	
    	disruptionBudget = this.disruptionBudget;
    	
    	Disruptions h = new Disruptions();
	    Double time = parameterValues[0];
	    //Double time = 0.0;

    	
	    //see email from Abhishek Disruption Chart on 9/13/2022

	    //normalize
	    //double[] newParameterValues = new double[21]; //ignore 0
	    Double[] newParameterValues = new Double[7]; //ignore 0

	    double mag = 0;
	    for (int i=1; i<newParameterValues.length; i++) {
	    	
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	mag = mag + parameterValues[i]; 
	    	
	    }
	    
	    //mag = Math.sqrt(mag);
	    
	    //normalize, then mult times budget
	    for (int i=1; i<newParameterValues.length; i++) {
	    	
	    	//newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	newParameterValues[i] = (parameterValues[i] / mag) * disruptionBudget; //div by mag, mult times budget
	    	
	    }
	    
	    //so basically, disruptions with lower weights are more expensive
	    //so for example if Halting is .5 weight, that means a budget of 1 will actually be .5 effective in halting
	    if (apply_weights == true){
	        
	        /*
	        double[]  budget_weight = 
	        {0.0, 0.5, 1.0, 0.5, 1.0, 0.5,
	         1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
	          1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 
	          1.0, 1.0, 1.0};
	        */
	        
	        //second experiment
	        double[]  budget_weight = 
	        {0.0, 1.0, 1.0, 1.0, 1.0, 0.5,
	         1.0, 1.0, 1.0, 1.0, 1.0, 0.5,
	          1.0, 1.0, 1.0, 1.0, 1.0, 0.5, 
	          1.0, 1.0, 1.0};
	        
			for (int i=1; i<newParameterValues.length; i++) {
			
		
			
				newParameterValues[i] = newParameterValues[i] * budget_weight[i];
			
			}	        
	    }
	    
	    
	    
	    h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "RawMaterialSupplier", time, newParameterValues[1]);
	    h.add(Enum.valueOf(Type.class,  "Halt"), "ApiProduction", time, newParameterValues[2]);	    
	    h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "ExcipientSupplier", time, newParameterValues[3]);
	    h.add(Enum.valueOf(Type.class,  "Halt"), "DrugProduction", time, newParameterValues[4]);
	    h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "PacMatSupplier", time, newParameterValues[5]);
	    h.add(Enum.valueOf(Type.class,  "Halt"), "Packaging", time, newParameterValues[6]);
	    

	    this.disruptions = h;
	    
    	
    }
    
    

    
}
