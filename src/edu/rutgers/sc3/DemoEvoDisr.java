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
public class DemoEvoDisr extends Demo {

    public DemoEvoDisr(long seed)    {
    	
	super(seed);
	
	Demo.quiet = false;
	Demo.verbose = false;
	
	try {
		mySetUp();
		
		
	} catch (Exception e) {
		e.printStackTrace();
	}
	
	System.out.println("sc3.DemoEvoDisr()");
	
    }
    



	//setup without commandline parameters, 
	public void mySetUp() throws IOException, IllegalInputException{

        //String confPath = "../config/sc2_1000.csv";

        //String confPath = "../config/sc3.csv";

        String confPath = "config/sc3.csv";

        File f= new File(confPath);
	    this.config  = Config.readConfig(f);
	    
	    //for testing
	    //double[] parameterValues = new double[92];
	    //parameterValues[43] = 400.0;
	    //parameterValues[89] = 1.0;
	    //setOptimizationParameters(parameterValues) ;
	    //double[] parameterValues = {0.4348604571496092 0.8675479050764301 0.03539268662993739 0.408989155700802 0.48851475910127823 0.5477983523908055 0.39843098477793476 0.4557794371648183 0.5016929496495401 0.28188676237429544 0.9615762078528728 0.7113111304275022 0.8182222570205602 0.398986037629405 0.3544381837506195 0.2615840578384691 0.6490448204254604 0.48660133329081773 0.8366848919334856 0.6818243693075076 0.2620340606719293 0.6953407807430199 0.27189999532145337 0.5356438623560275 0.5028810407842108 0.2875001725640978 0.43623057197568715 0.3974095953039962 0.696878999160671 0.6761422162213762};
	    
	    double [] parameterValues = {0.4348604571496092, 0.8675479050764301, 0.03539268662993739, 0.408989155700802, 0.48851475910127823, 0.5477983523908055, 0.39843098477793476, 0.4557794371648183, 0.5016929496495401, 0.28188676237429544, 0.9615762078528728, 0.7113111304275022, 0.8182222570205602, 0.398986037629405, 0.3544381837506195, 0.2615840578384691, 0.6490448204254604, 0.48660133329081773, 0.8366848919334856, 0.6818243693075076, 0.2620340606719293, 0.6953407807430199, 0.27189999532145337, 0.5356438623560275, 0.5028810407842108, 0.2875001725640978, 0.43623057197568715, 0.3974095953039962, 0.696878999160671, 0.6761422162213762};

	    
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
       	
    
    assessment[0] = aw.avgT;  //avg wait time from all orders

    
    return assessment;
    }
    
    //Raj CMAES methods
    //The optimizer uses this to input 
    public void setOptimizationParameters(double[] parameterValues) {
    	

        int time=100;
        
        double disruptionBudget = 400.0;
        
        double[] newParameterValues = new double[parameterValues.length];

        

	    double mag = 0;
	    for (int i=0; i<parameterValues.length; i++) {
	    	
	    	mag = mag + parameterValues[i]; 
	    	
	    }

	    //normalize, then mult times budget
	    for (int i=0; i<parameterValues.length; i++) {
	    	
	    	newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	//System.out.println(newParameterValues[i]);
	    	//newParameterValues[i] = 0.0;
	    	
	    }
	    
	    //System.exit(-1);
	    
	    //newParameterValues[15] = 800.0;
	    //newParameterValues[12] = 400.0;
	    
	    //System.out.println("A: "+this.disruptions);
	    
	    Disruptions h = new Disruptions();

	    //if (newParameterValues[0] >= 1.0) {h.add(Enum.valueOf(Type.class,  "Halt"), "eeRMSupplier", i,1);

	    

		
	        if (newParameterValues[0] >= 1.0) {h.add(Type.Halt, "prepregProd", time, newParameterValues[0], 1);}
			if (newParameterValues[1] >= 1.0) {h.add(Type.Halt, "substrateSmallProd", time, newParameterValues[1], 1);}
			if (newParameterValues[2] >= 1.0) {h.add(Type.Halt, "substrateLargeProd", time, newParameterValues[2], 1);}
			if (newParameterValues[3] >= 1.0) {h.add(Type.Halt, "cellProd", time, newParameterValues[3], 1);}
			if (newParameterValues[4] >= 1.0) {h.add(Type.Halt, "cellAssembly", time, newParameterValues[4], 1);}
			if (newParameterValues[5] >= 1.0) {h.add(Type.Halt, "cellPackaging", time, newParameterValues[5], 1);}
			if (newParameterValues[6] >= 1.0) {h.add(Type.Halt, "arraySmallAssembly", time, newParameterValues[6], 1);}
			if (newParameterValues[7] >= 1.0) {h.add(Type.Halt, "arrayLargeAssembly", time, newParameterValues[7], 1);}

			if (newParameterValues[8] >= 1.0) {h.add(Type.TransDelayFactor, "arraySmallAssembly.adhesive", time, newParameterValues[8], 8);}
			if (newParameterValues[9] >= 1.0) {h.add(Type.TransDelayFactor, "arraySmallAssembly.diode", time, newParameterValues[9], 8);}
			if (newParameterValues[10] >= 1.0) {h.add(Type.TransDelayFactor, "cellPackaging.cellPM", time, newParameterValues[10], 8);}
			if (newParameterValues[11] >= 1.0) {h.add(Type.TransDelayFactor, "cellProd.cellRM", time, newParameterValues[11], 8);}
			if (newParameterValues[12] >= 1.0) {h.add(Type.TransDelayFactor, "cellProd.coverglass", time, newParameterValues[12], 8);}
			if (newParameterValues[13] >= 1.0) {h.add(Type.TransDelayFactor, "prepregProd.fiber", time, newParameterValues[13], 8);}
			if (newParameterValues[14] >= 1.0) {h.add(Type.TransDelayFactor, "prepregProd.resin", time, newParameterValues[14], 8);}
			if (newParameterValues[15] >= 1.0) {h.add(Type.TransDelayFactor, "substrateSmallProd.aluminum", time, newParameterValues[15], 8);}
			if (newParameterValues[16] >= 1.0) {h.add(Type.TransDelayFactor, "prepregProd", time, newParameterValues[16], 8);}
			if (newParameterValues[17] >= 1.0) {h.add(Type.TransDelayFactor, "substrateSmallProd", time, newParameterValues[17], 8);}
			if (newParameterValues[18] >= 1.0) {h.add(Type.TransDelayFactor, "substrateLargeProd", time, newParameterValues[18], 8);}
			if (newParameterValues[19] >= 1.0) {h.add(Type.TransDelayFactor, "cellProd", time, newParameterValues[19], 8);}
			if (newParameterValues[20] >= 1.0) {h.add(Type.TransDelayFactor, "cellAssembly", time, newParameterValues[20], 8);}
			if (newParameterValues[21] >= 1.0) {h.add(Type.TransDelayFactor, "cellPackaging", time, newParameterValues[21], 8);}
			if (newParameterValues[22] >= 1.0) {h.add(Type.TransDelayFactor, "arraySmallAssembly", time, newParameterValues[22], 8);}
			if (newParameterValues[23] >= 1.0) {h.add(Type.TransDelayFactor, "arrayLargeAssembly", time, newParameterValues[23], 8);}

			if (newParameterValues[24] >= 1.0) {h.add(Type.Adulteration, "prepregProd", time, newParameterValues[24], 0.3);}
			if (newParameterValues[25] >= 1.0) {h.add(Type.Adulteration, "substrateSmallProd", time, newParameterValues[25], 0.3);}
			if (newParameterValues[26] >= 1.0) {h.add(Type.Adulteration, "substrateLargeProd", time, newParameterValues[26], 0.3);}
			if (newParameterValues[27] >= 1.0) {h.add(Type.Adulteration, "cellAssembly", time, newParameterValues[27], 0.3);}
			if (newParameterValues[28] >= 1.0) {h.add(Type.Adulteration, "arraySmallAssembly", time, newParameterValues[28], 0.3);}
			if (newParameterValues[29] >= 1.0) {h.add(Type.Adulteration, "arrayLargeAssembly", time, newParameterValues[29], 0.3);}
	    
	    this.disruptions = h;

    	//System.out.println("B: "+this.disruptions);

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
