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
public class DemoEvoDisr extends Demo {

    public DemoEvoDisr(long seed)    {
    	
	super(seed);
	
	Demo.quiet = true;
	Demo.verbose = false;
	
	try {
		mySetUp();
		
		
	} catch (Exception e) {
		e.printStackTrace();
	}
	
	System.out.println("sc2.DemoEvoDisr()");
	
    }
    



	//setup without commandline parameters, 
	public void mySetUp() throws IOException, IllegalInputException{

        //String confPath = "../config/sc2_1000.csv";

        String confPath = "config/sc2_1000.csv";

        File f= new File(confPath);
	    this.config  = Config.readConfig(f);
	    
	    //for testing
	    //double[] parameterValues = new double[92];
	    //parameterValues[43] = 400.0;
	    //parameterValues[89] = 1.0;
	    //setOptimizationParameters(parameterValues) ;
	}
	
    /** Here, the supply network elements are added to the Demo object */
    public void start(){

	super.start();
    }



    public void	finish() {
    
        super.finish();
        double avgWaiting = this.wpq.sumWaiting/ this.wpq.nWaiting;

        System.out.println(avgWaiting);
    
    }
    
	public java.lang.Class simulationClass() {
	    return DemoEvoDisr.class;
	}	    
    
    
    public double[] assess(int numObjectives)
    {
    	
    double[] assessment = new double[numObjectives];
    
    System.out.println("this.wpq.sumWaiting "+this.wpq.sumWaiting);
    System.out.println("this.wpq.nWaiting "+this.wpq.nWaiting);
    
    double avgWaiting = this.wpq.sumWaiting/ this.wpq.nWaiting;
	double finalWaiting = this.wpq.getAvailable();

    System.out.println("avgWaiting "+avgWaiting);

    assessment[0] = avgWaiting;


    
    return assessment;
    } 
    
    //Raj CMAES methods
    //The optimizer uses this to input 
    public void setOptimizationParameters(double[] parameterValues) {
    	


        double[] start_times_double = new double[parameterValues.length/2];
        double[] end_times_double = new double[parameterValues.length/2];
    	
    	//split parameterValues
    	int q=0;
    	
    	for (int i=0; i<start_times_double.length; i++){
    	    start_times_double[i] = parameterValues[q];
    	    q = q + 1;
    	}

    	for (int i=0; i<end_times_double.length; i++){
    	    end_times_double[i] = parameterValues[q];
    	    q = q + 1;

    	}
    	
    	//start times we don't need to scale, so just convert to int
        int[] start_times = new int[start_times_double.length];
    	for (int i=0; i<start_times_double.length; i++){
    	    start_times[i] = (int)start_times_double[i];
    	} 	
    	
    	
    	double disruptionBudget = 200.0; //days
	    //Double time = 0.0;

    	
	    //see email from Abhishek Disruption Chart on 9/13/2022

	    //normalize
	    
	    int[] end_times = new int[end_times_double.length];

	    


	    double mag = 0;
	    for (int i=0; i<end_times_double.length; i++) {
	    	
	    	//mag = mag + (parameterValues[i] * parameterValues[i]); //square each element
	    	mag = mag + end_times_double[i]; 
	    	
	    }
	    
	    
	    //normalize, then mult times budget
	    for (int i=0; i<end_times_double.length; i++) {
	    	
	    	//newParameterValues[i] = (double)Math.round((parameterValues[i] / mag) * disruptionBudget); //div by mag, mult times budget
	    	end_times[i] =  (int)(Math.round((end_times_double[i] / mag) * disruptionBudget)); //div by mag, mult times budget
	    	
	    }
	    
	    
	    Disruptions h = new Disruptions();
	    //add the disruptions
	    
	    //400-800	dsDC	Depletion	20000

        //end_times[0]-1 because we don't want inclusive
        //so for example if 1 day at time 80, we only want to at 80, NOT 81
		if (end_times[0] > 0){
			for (int i=start_times[0]; i<start_times[0]+end_times[0]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "eeRMSupplier", i,1);
			}
		}
		if (end_times[1] > 0){
			for (int i=start_times[1]; i<start_times[1]+end_times[1]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "eeCmoProd.RMEE", i,20000);
			}
		}
		if (end_times[2] > 0){
			for (int i=start_times[2]; i<start_times[2]+end_times[2]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "eeRMSupplier", i,20000);
			}
		}
		if (end_times[3] > 0){
			for (int i=start_times[3]; i<start_times[3]+end_times[3]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "eeCmoProd", i,1);
			}
		}
		if (end_times[4] > 0){
			for (int i=start_times[4]; i<start_times[4]+end_times[4]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "eeCmoProd", i,20000);
			}
		}
		if (end_times[5] > 0){
			for (int i=start_times[5]; i<start_times[5]+end_times[5]; i++){
				h.add(Enum.valueOf(Type.class,  "Adulteration"), "eeCmoProd", i,0.75);
			}
		}
		if (end_times[6] > 0){
			for (int i=start_times[6]; i<start_times[6]+end_times[6]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "eePMSupplier", i,1);
			}
		}
		if (end_times[7] > 0){
			for (int i=start_times[7]; i<start_times[7]+end_times[7]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "eePMSupplier", i,20000);
			}
		}
		if (end_times[8] > 0){
			for (int i=start_times[8]; i<start_times[8]+end_times[8]; i++){
				h.add(Enum.valueOf(Type.class,  "DisableTrackingSafetyStock"), "eePackaging.PMEE", i,1);
			}
		}
		if (end_times[9] > 0){
			for (int i=start_times[9]; i<start_times[9]+end_times[9]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "eePackaging.PMEE", i,20000);
			}
		}
		if (end_times[10] > 0){
			for (int i=start_times[10]; i<start_times[10]+end_times[10]; i++){
				h.add(Enum.valueOf(Type.class,  "Adulteration"), "eePackaging", i,0.75);
			}
		}
		if (end_times[11] > 0){
			for (int i=start_times[11]; i<start_times[11]+end_times[11]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "dsRMSupplier", i,1);
			}
		}
		if (end_times[12] > 0){
			for (int i=start_times[12]; i<start_times[12]+end_times[12]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "dsRMSupplier", i,20000);
			}
		}
		if (end_times[13] > 0){
			for (int i=start_times[13]; i<start_times[13]+end_times[13]; i++){
				h.add(Enum.valueOf(Type.class,  "Adulteration"), "dsRMSupplier", i,0.75);
			}
		}
		if (end_times[14] > 0){
			for (int i=start_times[14]; i<start_times[14]+end_times[14]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "dsProd", i,1);
			}
		}
		if (end_times[15] > 0){
			for (int i=start_times[15]; i<start_times[15]+end_times[15]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "dsProd.RMDS", i,20000);
			}
		}
		if (end_times[16] > 0){
			for (int i=start_times[16]; i<start_times[16]+end_times[16]; i++){
				h.add(Enum.valueOf(Type.class,  "DisableTrackingSafetyStock"), "dsProd.DS", i,1);
			}
		}
		if (end_times[17] > 0){
			for (int i=start_times[17]; i<start_times[17]+end_times[17]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "dsCmoProd", i,1);
			}
		}
		if (end_times[18] > 0){
			for (int i=start_times[18]; i<start_times[18]+end_times[18]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "dsCmoProd.RMDS", i,20000);
			}
		}
		if (end_times[19] > 0){
			for (int i=start_times[19]; i<start_times[19]+end_times[19]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "dsCmoProd", i,20000);
			}
		}
		if (end_times[20] > 0){
			for (int i=start_times[20]; i<start_times[20]+end_times[20]; i++){
				h.add(Enum.valueOf(Type.class,  "Adulteration"), "dsProd", i,0.75);
			}
		}
		if (end_times[21] > 0){
			for (int i=start_times[21]; i<start_times[21]+end_times[21]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "dsPMSupplier", i,1);
			}
		}
		if (end_times[22] > 0){
			for (int i=start_times[22]; i<start_times[22]+end_times[22]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "dsPMSupplier", i,20000);
			}
		}
		if (end_times[23] > 0){
			for (int i=start_times[23]; i<start_times[23]+end_times[23]; i++){
				h.add(Enum.valueOf(Type.class,  "DisableTrackingSafetyStock"), "dsPackaging.PMDS", i,1);
			}
		}
		if (end_times[24] > 0){
			for (int i=start_times[24]; i<start_times[24]+end_times[24]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "dsPackaging.PMDS", i,20000);
			}
		}
		if (end_times[25] > 0){
			for (int i=start_times[25]; i<start_times[25]+end_times[25]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "dsPackaging", i,1);
			}
		}
		if (end_times[26] > 0){
			for (int i=start_times[26]; i<start_times[26]+end_times[26]; i++){
				h.add(Enum.valueOf(Type.class,  "Adulteration"), "dsPackaging", i,0.75);
			}
		}
		if (end_times[27] > 0){
			for (int i=start_times[27]; i<start_times[27]+end_times[27]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "eeDC.eeHEP", i,20000);
			}
		}
		if (end_times[28] > 0){
			for (int i=start_times[28]; i<start_times[28]+end_times[28]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "eeDC.eeDP", i,20000);
			}
		}
		if (end_times[29] > 0){
			for (int i=start_times[29]; i<start_times[29]+end_times[29]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "dsDC.dsDP", i,20000);
			}
		}
		if (end_times[30] > 0){
			for (int i=start_times[30]; i<start_times[30]+end_times[30]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "dsDC.dsHEP", i,20000);
			}
		}
		if (end_times[31] > 0){
			for (int i=start_times[31]; i<start_times[31]+end_times[31]; i++){
				h.add(Enum.valueOf(Type.class,  "ShipmentLoss"), "dsDP.dsHEP", i,20000);
			}
		}
		if (end_times[32] > 0){
			for (int i=start_times[32]; i<start_times[32]+end_times[32]; i++){
				h.add(Enum.valueOf(Type.class,  "StopInfoFlow"), "eeDP.eeHEP", i,1);
			}
		}
		if (end_times[33] > 0){
			for (int i=start_times[33]; i<start_times[33]+end_times[33]; i++){
				h.add(Enum.valueOf(Type.class,  "StopInfoFlow"), "eeMedTech.eeHEP", i,1);
			}
		}
		if (end_times[34] > 0){
			for (int i=start_times[34]; i<start_times[34]+end_times[34]; i++){
				h.add(Enum.valueOf(Type.class,  "StopInfoFlow"), "eeDC.eeHEP", i,1);
			}
		}
		if (end_times[35] > 0){
			for (int i=start_times[35]; i<start_times[35]+end_times[35]; i++){
				h.add(Enum.valueOf(Type.class,  "StopInfoFlow"), "dsDC.dsHEP", i,1);
			}
		}
		if (end_times[36] > 0){
			for (int i=start_times[36]; i<start_times[36]+end_times[36]; i++){
				h.add(Enum.valueOf(Type.class,  "Halt"), "eePackaging", i,1);
			}
		}
		if (end_times[37] > 0){
			for (int i=start_times[37]; i<start_times[37]+end_times[37]; i++){
				h.add(Enum.valueOf(Type.class,  "StopInfoFlow"), "eeDP.eeHEP", i,1);
			}
		}
		if (end_times[38] > 0){
			for (int i=start_times[38]; i<start_times[38]+end_times[38]; i++){
				h.add(Enum.valueOf(Type.class,  "StopInfoFlow"), "dsDP.dsHEP", i,1);
			}
		}
		if (end_times[39] > 0){
			for (int i=start_times[39]; i<start_times[39]+end_times[39]; i++){
				h.add(Enum.valueOf(Type.class,  "StopInfoFlow"), "eeMedTech.eeDC", i,1);
			}
		}
		if (end_times[40] > 0){
			for (int i=start_times[40]; i<start_times[40]+end_times[40]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "eeDC", i,20000);
			}
		}
		if (end_times[41] > 0){
			for (int i=start_times[41]; i<start_times[41]+end_times[41]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "eeDP", i,20000);
			}
		}
		if (end_times[42] > 0){
			for (int i=start_times[42]; i<start_times[42]+end_times[42]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "eeHEP", i,20000);
			}
		}
		if (end_times[43] > 0){
			for (int i=start_times[43]; i<start_times[43]+end_times[43]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "dsDC", i,20000);
			}
		}
		if (end_times[44] > 0){
			for (int i=start_times[44]; i<start_times[44]+end_times[44]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "dsDP", i,20000);
			}
		}
		if (end_times[45] > 0){
			for (int i=start_times[45]; i<start_times[45]+end_times[45]; i++){
				h.add(Enum.valueOf(Type.class,  "Depletion"), "dsHEP", i,20000);
			}
		}



		//System.out.println(h);
		//System.exit(-1);



	    this.disruptions = h;
	    
    	
    }
    

    
    public static void main(String[] argv) throws IOException, IllegalInputException {

    for (String a : argv) {
    	System.out.println(a);
    }
    

    	
    //System.out.println(argv.length);
    
    //System.exit(-1);
    
    String[] new_argv = new String[4];
    new_argv[0] = "-until"; //instead of -until
    new_argv[1] = "2000"; //opt ignores anyway
    new_argv[2] = "-config";
    new_argv[3] = "config/sc2_1000.csv";
    System.out.println(new_argv[3]);
    //new_argv[1] = "100000"; //opt ignores anyway

    //new_argv[2] = "-disrupt";
    //new_argv[3] = "../config/dis.A2.csv";

	
	doLoop(DemoEvoDisr.class, new_argv);
	//doLoop(maker, argv);C
	
	System.exit(0);
    }

}
