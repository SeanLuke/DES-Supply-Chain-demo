package edu.rutgers.sc2;

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
/** The main class for the SC-2 model

<pre>
Demo demo = ....;
run simulation
demo.wpq.getAvailable() get at every step
ALSO will add a method to get the average waiting queue size
demo.wpq.sumWaiting gives you the integral of the above over all 2000 days of simulation
("patient-days wasted waiting in line")

 */
public class DemoEvoTargetLvlOpt extends Demo {

    public DemoEvoTargetLvlOpt(long seed)    {
    	
	super(seed);
	
	Demo.quiet = true;
	Demo.verbose = false;
	
	try {
		mySetUp();
		
		
	} catch (Exception e) {
		e.printStackTrace();
	}
	
	System.out.println("sc2.DemoEvoTargetLvlOpt()");
	
    }
    



	//setup without commandline parameters, 
	public void mySetUp() throws IOException, IllegalInputException{

        String confPath = "../config/sc2_1000_NO_TARGET.csv"; //added in in setOptParameters

        //String confPath = "config/sc2_1000.csv";

        File f= new File(confPath);
	    this.config  = Config.readConfig(f);
	    
	    String disruptPath = "../endonly_ind.csv"; //best disruption
		this.disruptions  = Disruptions.readList(new File(disruptPath));


	}
	
    /** Here, the supply network elements are added to the Demo object */
    public void start(){

	super.start();
    }



    public void	finish() {
    
        super.finish();
        double avgWaiting = this.wpq.sumWaiting/ this.wpq.nWaiting;

        System.out.println(-1.0*avgWaiting);
    
    }
    
	public java.lang.Class simulationClass() {
	    return DemoEvoTargetLvlOpt.class;
	}	    
    
    
    public double[] assess(int numObjectives)
    {
    	
    double[] assessment = new double[numObjectives];
    
    System.out.println("this.wpq.sumWaiting "+this.wpq.sumWaiting);
    System.out.println("this.wpq.nWaiting "+this.wpq.nWaiting);
    
    double avgWaiting = this.wpq.sumWaiting/ this.wpq.nWaiting;
	double finalWaiting = this.wpq.getAvailable();

    System.out.println("avgWaiting "+avgWaiting);

    assessment[0] = -1.0 * avgWaiting; //target levels maximized negative ppl waiting (minimize ppl waiting)


    
    return assessment;
    } 
    
    //Raj CMAES methods
    //disruptions at 0
    public void setOptimizationParameters(double[] parameterValues) {
    	
    	boolean scale_proportional = true;
    	
    	double targetLevelBudget = 295740; //total target levels in our default config

	    Double[] newParameterValues = new Double[10]; //ignore 0

	    double mag = 0;
	    for (int i=0; i<newParameterValues.length; i++) {
	    	
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	mag = mag + parameterValues[i]; 
	    	
	    }
	    
	    //mag = Math.sqrt(mag);
	    
	    //normalize, then mult times budget
	    for (int i=0; i<newParameterValues.length; i++) {
	    	
	    	//newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	newParameterValues[i] = (parameterValues[i] / mag) * targetLevelBudget; //div by mag, mult times budget
	    	
	    }

		/*	    
		#eeCmoProd.safety.RMEE,targetLevel,10362
		#eePackaging.safety.PMEE,targetLevel,1500
		#eeDC,targetLevel,1000
		#eeDP,targetLevel,1036
		#eeHEP,targetLevel,1000
		#dsProd.safety.DS,targetLevel,65268
		#dsPackaging.safety.PMDS,targetLevel,65268
		#dsDC,targetLevel,70707
		#dsDP,targetLevel,7071
		#dsHEP,targetLevel,72528

		TOTAL : 295,740
		*/
		

	  
	  if (scale_proportional == false){
    	
      String name = "eeCmoProd.safety.RMEE";
      double target = Math.round(newParameterValues[0]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
	    
      name = "eePackaging.safety.PMEE";
      target = Math.round(newParameterValues[1]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "eeDC";
      target = Math.round(newParameterValues[2]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "eeDP";
      target = Math.round(newParameterValues[3]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "eeHEP";
      target = Math.round(newParameterValues[4]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "dsProd.safety.DS";
      target = Math.round(newParameterValues[5]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "dsPackaging.safety.PMDS";
      target = Math.round(newParameterValues[6]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "dsDC";
      target = Math.round(newParameterValues[7]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "dsDP";
      target = Math.round(newParameterValues[8]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      name = "dsHEP";
      target = Math.round(newParameterValues[9]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      }
     
     
	  //I don't think this is necessarily a good idea
	  //less so on reorder point I guess, but on initial?
	  
	  //total initial and total reorder are no longer "constant"
	        
	  else {
	  
	  //eeCmoProd.safety.RMEE,initial,10362
      //eeCmoProd.safety.RMEE,targetLevel,10362
      //eeCmoProd.safety.RMEE,reorderPoint,6908
    	
      String name = "eeCmoProd.safety.RMEE";
      double target = Math.round(newParameterValues[0]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ ((2.0/3.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));


        //eePackaging.safety.PMEE,initial,1500
        //eePackaging.safety.PMEE,targetLevel,1500
       //eePackaging.safety.PMEE,reorderPoint,1000
	    
      name = "eePackaging.safety.PMEE";
      target = Math.round(newParameterValues[1]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ ((2.0/3.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      //eeDC,initial,1000
      //eeDC,reorderPoint,500
      //eeDC,targetLevel,1000
      
      
      name = "eeDC";
      target = Math.round(newParameterValues[2]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));

	  //eeDP,initial,692
	  //eeDP,reorderPoint,692
	  //eeDP,targetLevel,1036    
	    
      name = "eeDP";
      target = Math.round(newParameterValues[3]);
      config.addNewParameter(name, "initial", ""+((173.0/259.0)*target));  
      config.addNewParameter(name, "reorderPoint", ""+ ((173.0/259.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));

      //eeHEP,initial,1000   
      //eeHEP,reorderPoint,500
      //eeHEP,targetLevel,1000
      
      name = "eeHEP";
      target = Math.round(newParameterValues[4]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ (0.5*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));

      //dsProd.safety.DS,initial,65268
      //dsProd.safety.DS,targetLevel,65268
      //dsProd.safety.DS,reorderPoint,43512
      
      name = "dsProd.safety.DS";
      target = Math.round(newParameterValues[5]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ ((2.0/3.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));


      //dsPackaging.safety.PMDS,initial,65268
      //dsPackaging.safety.PMDS,targetLevel,65268
      //dsPackaging.safety.PMDS,reorderPoint,43512
      
      name = "dsPackaging.safety.PMDS";
      target = Math.round(newParameterValues[6]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ ((2.0/3.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));

	  //dsDC,initial,65268
	  //dsDC,reorderPoint,48951
	  //dsDC,targetLevel,70707
           
      name = "dsDC";
      target = Math.round(newParameterValues[7]);
      config.addNewParameter(name, "initial", ""+((12.0/13.0)*target));  
      config.addNewParameter(name, "reorderPoint", ""+ ((9.0/13.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));

      //dsDP,initial,7260
     //dsDP,reorderPoint,4895
     //dsDP,targetLevel,7071
      
      name = "dsDP";
      target = Math.round(newParameterValues[8]);
      config.addNewParameter(name, "initial", ""+((2420.0/2357.0)*target));  
      config.addNewParameter(name, "reorderPoint", ""+ ((4895.0/7071.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));

      //dsHEP,initial,72528
      //dsHEP,reorderPoint,48352
      //dsHEP,targetLevel,72528
      
      name = "dsHEP";
      target = Math.round(newParameterValues[9]);
      config.addNewParameter(name, "initial", ""+target);  
      config.addNewParameter(name, "reorderPoint", ""+ ((2.0/3.0)*target));
      config.addNewParameter(name, "targetLevel", ""+ (target));
      
      }      
      
      
      
      
    }
    

    
    public static void main(String[] argv) throws IOException, IllegalInputException {

    for (String a : argv) {
    	System.out.println(a);
    }
    

    	
    //System.out.println(argv.length);
    
    //System.exit(-1);
    
    //String[] new_argv = new String[4];
    //new_argv[0] = "-until"; //instead of -until
    //new_argv[1] = "2000"; //opt ignores anyway
    //new_argv[2] = "-config";
    //new_argv[3] = "config/sc2_1000.csv";
    //System.out.println(new_argv[3]);
    //new_argv[1] = "100000"; //opt ignores anyway

    //new_argv[2] = "-disrupt";
    //new_argv[3] = "../config/dis.A2.csv";

	
	doLoop(DemoEvoTargetLvlOpt.class, argv);
	//doLoop(maker, argv);C
	
	System.exit(0);
    }

}
