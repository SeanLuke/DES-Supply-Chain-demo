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
public class DemoOptSafetyStocks extends Demo {


    
    public DemoOptSafetyStocks(long seed)    {
    	
	super(seed);
	
	Demo.quiet = true;
	Demo.verbose = false;
	
	try {
		mySetUp();
		
		
	} catch (Exception e) {
		e.printStackTrace();
	}
	
	System.out.println("pharma3.DemoOptSafetyStocks()");
	
    }
    



	//setup without commandline parameters, 
	public void mySetUp() throws IOException, IllegalInputException{
		
	    


	    //String confPath = "../config/pharma3.orig.NO_SAFETY.csv";
	    String confPath = "../config/pharma3.nosafety.csv";

	    //String confPath = "../config/pharma3.nosafety.csv";

	    //String confPath = "../config/pharma3.orig zeroed_out_safety.csv";

	    String disruptPath = "../optimize_output/Jan10/individual_optimization/disruptions/200gen_20lambda_best_disruption_9.csv";
	    String chartsPath = "charts";
		this.disruptions  = Disruptions.readList(new File(disruptPath));


	
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
        System.out.println(pharmaCompany.getDistributor().getEverReceived());
    
    }




	public java.lang.Class	simulationClass() {
	    //return DemoNoCommandLineArg.class;
	    return DemoOptSafetyStocks.class;
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

	
	doLoop(DemoOptSafetyStocks.class, new_argv);
	//doLoop(maker, argv);C
	
	System.exit(0);
    }
    
    public double[] assess(int numObjectives)
    {
    	
    double[] assessment = new double[numObjectives];
    
    //positive, we want to maximize given our distribution stocks
    assessment[0] = pharmaCompany.getDistributor().getEverReceived();

    //assessment[0] = disruptions.data.elementAt(0).magnitude;

    
    return assessment;
    } 
    
    //Raj CMAES methods
    //The optimizer uses this to input 
    public void setOptimizationParameters(double[] parameterValues) {
    	
    	//System.out.println("rabbit");
    	//System.exit(-1);
    	
    	//RM, Excipient, PacMat;  API,  Bulk drug
    	
    	double safetyStockBudget = 5E9;
    	// 5E7 and increase by 5E6 until we reach 2E10
    	


    	

	    //normalize
	    //double[] newParameterValues = new double[21]; //ignore 0
	    Double[] newParameterValues = new Double[5]; //ignore 0

	    double mag = 0;
	    for (int i=0; i<newParameterValues.length; i++) {
	    	
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	mag = mag + parameterValues[i]; 
	    	
	    }
	    
	    //mag = Math.sqrt(mag);
	    
	    //normalize, then mult times budget
	    for (int i=0; i<newParameterValues.length; i++) {
	    	
	    	//newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	newParameterValues[i] = (parameterValues[i] / mag) * safetyStockBudget; //div by mag, mult times budget
	    	
	    }
    	
      String name = "ApiProduction.safety.RawMaterial";
      double target = Math.round(newParameterValues[0]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
		
      name = "DrugProduction.safety.Excipient";
      target = Math.round(newParameterValues[1]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  

		
      name = "Packaging.safety.PackagingMaterial";
      target = Math.round(newParameterValues[2]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
				
      name = "DrugProduction.safety.Api";
      target = Math.round(newParameterValues[3]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
	  config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
		
      name = "Packaging.safety.BulkDrug";
      target = Math.round(newParameterValues[4]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      //config.addNewParameter(name, "delay",  Util.array2vector( "Triangular", "7", "10", "15"));    	
      config.addNewParameter(name, "delay",  Util.array2vector( "Uniform", "400", "401"));  
				
		
	    
    	
    }
    
    

    
}
