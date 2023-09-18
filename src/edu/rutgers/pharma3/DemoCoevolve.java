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
public class DemoCoevolve extends Demo {


    
    public DemoCoevolve(long seed)    {
    	
	super(seed);
	
	Demo.quiet = true;
	Demo.verbose = false;
	
	try {
		mySetUp();
		
		/*
		double[] randVals = {266.1867263571677, 
		0.5970813675650875,
		0.5955199257882583,
		0.3210713350590342,
	    0.4950842466449613,
		0.9531448152094,
		0.8447456940261855,
	    0.5445114361547392,
		0.22755107698521648,
		0.9023200811505643,
		0.7921626568802624,
		0.43059819850523473,
		0.620837071661404,
		0.44094690967424166,
		0.3196054453196405,
		0.32301623110397826,
		0.18845253831572029,
		0.7028198520328125,
		0.8398761465516634,
		0.5242999802576959,
		0.6555705251546053,
		0.7988915327121033,
		0.8803516132871503,
		0.6393178686754513,
		0.7307965437427348,
		0.9138946970686843};
		
		setOptimizationParameters(randVals);
		*/
		
		
	} catch (Exception e) {
		e.printStackTrace();
	}
	
	System.out.println("pharma3.DemoCoevolve()");
	
    }
    



	//setup without commandline parameters, 
	public void mySetUp() throws IOException, IllegalInputException{


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
        //System.out.println(this.assess(1)[0]);
    
    }




	public java.lang.Class	simulationClass() {
	    return DemoCoevolve.class;
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
    
    String[] new_argv = argv;
    /*
    String[] new_argv = new String[2];
    new_argv[0] = "-until"; //instead of -until
    new_argv[1] = "365"; //opt ignores anyway
    */
    
    
    //new_argv[1] = "100000"; //opt ignores anyway

    //new_argv[2] = "-disrupt";
    //new_argv[3] = "../config/dis.A2.csv";

	
	doLoop(DemoCoevolve.class, new_argv);
	//doLoop(maker, argv);C
	
	System.exit(0);
    }
    
    public double[] assess(int numObjectives)
    {
    	
    double[] assessment = new double[numObjectives];
    
    //System.out.println("before mult "+pharmaCompany.getDistributor().getEverReceived());
    
    assessment[0] = -1.0 * pharmaCompany.getDistributor().getEverReceived();

    //assessment[0] = disruptions.data.elementAt(0).magnitude;
    //System.out.println("assess returns : "+assessment[0]);
    
    return assessment;
    } 
    
    //Raj CMAES methods
    //The optimizer uses this to input 
    public void setOptimizationParameters(double[] parameterValues) {
    	
    	//System.out.println("rabbit");
    	//System.exit(-1);
    	
    	
    	//System.out.println("inputted parameterVals");
    	//for (double d : parameterValues){
    	//    System.out.println(d);
    	//}
    	//System.out.println("---");


    	
    	boolean apply_weights = false;
    	
    	double disruptionBudget = 200.0;
    	Disruptions h = new Disruptions();
	    Double time = parameterValues[0];
    	
	    //see email from Abhishek Disruption Chart on 9/13/2022
	    //normalize
	    //double[] newParameterValues = new double[21]; //ignore 0
	    Double[] newParameterValues = new Double[21]; //ignore 0

	    double mag = 0;
	    for (int i=1; i<21; i++) {
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	mag = mag + parameterValues[i]; 
	    }
	    //normalize, then mult times budget
	    for (int i=1; i<21; i++) {
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
	        {0.0, 0.5, 1.0, 0.5, 1.0, 0.5,
	         1.0, 1.0, 1.0, 1.0, 1.0, 0.5,
	          1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 
	          1.0, 1.0, 1.0};
			for (int i=1; i<21; i++) {
				newParameterValues[i] = newParameterValues[i] * budget_weight[i];
			}	        
	    }
    
	    h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "RawMaterialSupplier", time, newParameterValues[1]);
	    h.add(Enum.valueOf(Type.class,  "Adulteration"), "RawMaterialSupplier", time, newParameterValues[2]);
	    h.add(Enum.valueOf(Type.class,  "Depletion"), "RawMaterial", time, newParameterValues[3]);
	    
	    h.add(Enum.valueOf(Type.class,  "Depletion"), "Api", time, newParameterValues[4]);
	    h.add(Enum.valueOf(Type.class,  "Halt"), "ApiProduction", time, newParameterValues[5]);
	    h.add(Enum.valueOf(Type.class,  "Adulteration"), "ApiProduction", time, newParameterValues[6]);
	    
	    h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "ExcipientSupplier", time, newParameterValues[7]);
	    h.add(Enum.valueOf(Type.class,  "Adulteration"), "ExcipientSupplier", time, newParameterValues[8]);
	    h.add(Enum.valueOf(Type.class,  "Depletion"), "Excipient", time, newParameterValues[9]);
	    
	    h.add(Enum.valueOf(Type.class,  "Depletion"), "BulkDrug", time, newParameterValues[10]);
	    h.add(Enum.valueOf(Type.class,  "Halt"), "DrugProduction", time, newParameterValues[11]);
	    h.add(Enum.valueOf(Type.class,  "Adulteration"), "DrugProduction", time, newParameterValues[12]);

	    h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "PacMatSupplier", time, newParameterValues[13]);
	    h.add(Enum.valueOf(Type.class,  "Adulteration"), "PacMatSupplier", time, newParameterValues[14]);
	    h.add(Enum.valueOf(Type.class,  "Depletion"), "PackagingMaterial", time, newParameterValues[15]);
	    
	    h.add(Enum.valueOf(Type.class,  "Depletion"), "Packaging", time, newParameterValues[16]);
	    h.add(Enum.valueOf(Type.class,  "Halt"), "Packaging", time, newParameterValues[17]);
	    h.add(Enum.valueOf(Type.class,  "Adulteration"), "Packaging", time, newParameterValues[18]);
	   	    
	    h.add(Enum.valueOf(Type.class,  "Depletion"), "Distributor", time, newParameterValues[19]);
	    h.add(Enum.valueOf(Type.class,  "Delay"), "Distributor", time, newParameterValues[20]);

	    this.disruptions = h;
	    
	    ///////////////////////////////////////
	    
	    double safetyStockBudget = 5E9;
    	// 5E7 and increase by 5E6 until we reach 2E10
	    //normalize
	    //double[] newParameterValues = new double[21]; //ignore 0
	    Double[] newParameterValues2 = new Double[5]; //ignore 0

	    double mag2 = 0;
	    for (int i=21; i<26; i++) {
	    	
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	mag2 = mag2 + parameterValues[i]; 
	    	
	    }	    
	    //mag = Math.sqrt(mag);
	    
	    //normalize, then mult times budget
	    for (int i=21; i<26; i++) {
	    	
	    	//newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	newParameterValues2[i-21] = (parameterValues[i] / mag2) * safetyStockBudget; //div by mag, mult times budget
	    	
	    }
    	
      String name = "ApiProduction.safety.RawMaterial";
      double target = Math.round(newParameterValues2[0]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  


		
      name = "DrugProduction.safety.Excipient";
      target = Math.round(newParameterValues2[1]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
		
      name = "Packaging.safety.PackagingMaterial";
      target = Math.round(newParameterValues2[2]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
				
      name = "DrugProduction.safety.Api";
      target = Math.round(newParameterValues2[3]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
				
      name = "Packaging.safety.BulkDrug";
      target = Math.round(newParameterValues2[4]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
			
	    
    	
    }
    
    

    
}
