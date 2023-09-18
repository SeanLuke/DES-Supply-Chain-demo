package edu.rutgers.sc3;

import edu.rutgers.supply.*;

import java.io.*;
import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import ec.util.MersenneTwisterFast;

import edu.rutgers.util.*;
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

//Raj's version of Demo for optimizing
/** The main class for the SC-3 model

<pre>
Demo demo = ....;
run simulation
demo.wpq.getAvailable() get at every step
ALSO will add a method to get the average waiting queue size
demo.wpq.sumWaiting gives you the integral of the above over all 2000 days of simulation
("patient-days wasted waiting in line")

 */
public class DemoEvoMitigations extends Demo {

    public DemoEvoMitigations(long seed)    {
    	
	super(seed);
	
	Demo.quiet = true;
	Demo.verbose = false;
	
	try {
		mySetUp();
		
		
	} catch (Exception e) {
		e.printStackTrace();
	}
	
	System.out.println("sc3.DemoEvoMitigations()");
	
    }
    



	//setup without commandline parameters, 
	public void mySetUp() throws IOException, IllegalInputException{

        //String confPath = "../config/sc2_1000.csv";

        //String confPath = "../config/sc3.csv";

        String confPath = "config/sc3.csv";

        File f= new File(confPath);
	    this.config  = Config.readConfig(f);
	    
	    String disruptPath = "../optimize_output/Sept4/Top5_Disr_Best_Disruption.csv";

	    
	    this.disruptions  = Disruptions.readList(new File(disruptPath));



	    
	    //for testing
	    //double[] parameterValues = new double[92];
	    //parameterValues[43] = 400.0;
	    //parameterValues[89] = 1.0;
	    //setOptimizationParameters(parameterValues) ;
	    double [] parameterValues = {0.26178390596756546, 0.40017398564132245, 0.4599766062539123, 0.9419187310144653, 0.31273560283013213, 0.2692189720408141, 0.41661423066903147, 0.8515416997474073, 0.6081255840434029, 0.08132372862415899, 0.07543792762247874, 0.47102134878564955};

	    
	    setOptimizationParameters(parameterValues) ;
	    
	}
	
    /** Here, the supply network elements are added to the Demo object */
    public void start(){

	super.start();
    }



    public void	finish() {
    
        super.finish();

        System.out.println("D: "+this.disruptions);
	    //EndCustomer.Stats[] stats = this.getWaitingStats();
       	//EndCustomer.Stats awf=stats[0], awu=stats[1], aw = stats[2];
        
        //System.out.println("------");
        //System.out.println("RAJ for "+awf.cnt+" filled orders " + awf.avgT   + " days");
        //System.out.println("RAJ for "+awu.cnt+" unfilled orders " + awu.avgT  + " days so far");
        //System.out.println( "RAJ for all "+aw.cnt+" orders " + aw.avgT     + " days so far");

        System.out.println("time : "+schedule.getTime());
    
    }
    
	public java.lang.Class simulationClass() {
	    return DemoEvoDisr.class;
	}	    
    
    
    public double[] assess(int numObjectives)
    {
    
    double[] assessment = new double[numObjectives];

    	
	EndCustomer.Stats[] stats = this.getWaitingStats();
    EndCustomer.Stats awf=stats[0], awu=stats[1], aw = stats[2];
       	
    
    assessment[0] = -1.0 * aw.avgT;  //avg wait time from all orders

    
    return assessment;
    }
    
    //Raj CMAES methods
    //The optimizer uses this to input 
    public void setOptimizationParameters(double[] parameterValues) {
    


    	double ssBudget =  Math.floor(120948.0); //total target levels in our default config

	    Double[] newParameterValues = new Double[7]; //first 7 are MTS safety stock

	    double mag = 0;
	    for (int i=0; i<newParameterValues.length; i++) {
	    	
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	mag = mag + parameterValues[i]; 
	    	
	    }
	    
	    //mag = Math.sqrt(mag);
	    
	    //normalize, then mult times budget
	    for (int i=0; i<newParameterValues.length; i++) {
	    	
	    	//newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	newParameterValues[i] = (parameterValues[i] / mag) * ssBudget; //div by mag, mult times budget
	    	
	    }
	    
	    double delayBudget = 196.0;
	    
		Double[] delayFeatures = new Double[5]; //second 7 are MTS safety stock
    

	    double delayMag = 0;
	    for (int i=newParameterValues.length; i<newParameterValues.length+delayFeatures.length; i++) {
	    	
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	delayMag = delayMag + parameterValues[i]; 
	    	
	    }
	    
	    //mag = Math.sqrt(mag);
	    
	    //normalize, then mult times budget
	    for (int i=newParameterValues.length; i<newParameterValues.length+delayFeatures.length; i++) {
	    	
	    	//newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	delayFeatures[i-newParameterValues.length] = (parameterValues[i] / delayMag) * delayBudget; //div by mag, mult times budget
	    	
	    }

    
    



//MTO: outputDelay ?
    	
//prodDelay and delay ?

      String name = "prepregProd.safety.fiber";  
      double target = Math.round(newParameterValues[0]);
      config.addNewParameter(name, "initial", ""+((904.0/2500.0)*target));  
      config.addNewParameter(name, "reorderPoint", ""+ ((1500.0/2500.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
	    
      name = "prepregProd.safety.resin";
      target = Math.round(newParameterValues[1]);
      config.addNewParameter(name, "initial", ""+(1.0)*target);  
      config.addNewParameter(name, "reorderPoint", ""+ ((149.0/298.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "substrateSmallProd.safety.prepreg";
      target = Math.round(newParameterValues[2]);
      config.addNewParameter(name, "initial", ""+((1836.0/10000.0)*target));  
      config.addNewParameter(name, "reorderPoint", ""+ ((0.5)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
            
      name = "cellProd.safety.cellRM";
      target = Math.round(newParameterValues[3]);
      config.addNewParameter(name, "initial", ""+(1.0)*target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "cellPackaging.safety.cellPM";
      target = Math.round(newParameterValues[4]);
      config.addNewParameter(name, "initial", ""+((22224.0/30000.0)*target));  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "arraySmallAssembly.safety.adhesive";
      target = Math.round(newParameterValues[5]);
      config.addNewParameter(name, "reorderPoint", ""+ ((100.0/150.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "arraySmallAssembly.safety.diode";
      target = Math.round(newParameterValues[6]);
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      
      //delays

      name = "substrateSmallProd.safety.aluminum";
      target = Math.round(delayFeatures[0]);
	  String[] aaa = new String[4];
	  aaa[0] = "Triangular";
	  aaa[1] = Double.toString(target);
	  aaa[2] = Double.toString(target+15);
	  aaa[3] = Double.toString(target+30);
      config.addNewParameter(name, "delay", Util.array2vector(aaa));
      
      
      name = "cellProd.safety.coverglass";
      target = Math.round(delayFeatures[1]);
	  aaa = new String[4];
	  aaa[0] = "Triangular";
	  aaa[1] = Double.toString(target);
	  aaa[2] = Double.toString(target+60);
	  aaa[3] = Double.toString(target+120);
      config.addNewParameter(name, "delay", Util.array2vector(aaa));
      
      
      name = "cellProd";
      target = Math.round(delayFeatures[2]);
	  aaa = new String[3];
	  aaa[0] = "Uniform";
	  aaa[1] = Double.toString(target);
	  aaa[2] = Double.toString(target+2);
      config.addNewParameter(name, "outputDelay", Util.array2vector(aaa));
      
      
      name = "substrateSmallProd";
      target = Math.round(delayFeatures[3]);
	  aaa = new String[3];
	  aaa[0] = "Uniform";
	  aaa[1] = Double.toString(target);
	  aaa[2] = Double.toString(target+7);
      config.addNewParameter(name, "outputDelay", Util.array2vector(aaa));
      

      name = "substrateLargeProd";
      target = Math.round(delayFeatures[3]);
	  aaa = new String[3];
	  aaa[0] = "Uniform";
	  aaa[1] = Double.toString(target);
	  aaa[2] = Double.toString(target+7);
      config.addNewParameter(name, "outputDelay", Util.array2vector(aaa));

      

        //finish above
     //substrateSmallProd.safety.aluminum,delay,Triangular,60,75,90
     //cellProd.safety.coverglass,delay,Triangular,120,180,240
     //cellProd,outputDelay,Uniform,2,4
    //substrateSmallProd,outputDelay,Uniform,7,14
    //substrateLargeProd,outputDelay,Uniform,7,14




    }
    

    
    public static void main(String[] argv) throws IOException, IllegalInputException {

    for (String a : argv) {
    	System.out.println(a);
    }
    
    String[] new_argv = new String[4];
    new_argv[0] = "-until"; //instead of -until
    new_argv[1] = "1825"; //opt ignores anyway
    new_argv[2] = "-config";
    new_argv[3] = "config/sc3.csv";

	
	doLoop(DemoEvoDisr.class, new_argv);
	//doLoop(maker, argv);C
	
	System.exit(0);
    }
    


}
