package edu.rutgers.pharma3;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;




import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.Population;
import ec.Problem;
import ec.coevolve.GroupedProblemForm;
import ec.eval.MasterProblem;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleFitness;
import ec.simple.SimpleProblemForm;
import ec.vector.DoubleVectorIndividual;
import ec.vector.IntegerVectorIndividual;
import sim.engine.SimState;
import sim.util.Properties;
import ec.util.Parameter;



public class Pharma3DemoCoevProblem2 extends Problem implements GroupedProblemForm
    {



    /** The *slave's* SimState.  This is used internally by the slave.  It won't be set in the master. */
    public SimState simstate;
                    
    public static final String P_SHOULD_SET_CONTEXT = "set-context";
    boolean shouldSetContext;

    public int numObjectives;
    public int maximumSteps;
    public double maximumTime;
    public int numTrials;
    public boolean rebuildSimState;   
    

    
	public int[] parameterType;
	public double[] parameterValue;
	
	public int[] indParameterIndex;
    boolean treatParametersAsArray;
    int fitnessType;
	
	public static final int TYPE_OTHER = 0;
    public static final int TYPE_DOUBLE = 1;
    public static final int TYPE_INT = 2;
    public static final int TYPE_BOOLEAN = 3;
    
    public static final int FITNESS_TYPE_SIMPLE = 0;
    public static final int FITNESS_TYPE_NSGA2 = 1;
    
    public String modelClassName = "edu.rutgers.pharma3.DemoCoevolve";


    boolean ready;
    Object[] lock = new Object[0];
    void raiseReady()
        {
        synchronized(lock) { ready = true; lock.notifyAll(); }
        }
    void blockOnReady()
        {
        synchronized(lock)
            {
            while (!ready)
                {
                // System.err.println("NOT READY!");
                try { lock.wait(); }
                catch (InterruptedException ex) 
                    {
                    System.err.println("Pharma3DemoCoevProblem2.blockOnReady: this should never happen");
                    }
                }
            }
        }
        
    public void setup(final EvolutionState state, final Parameter base) 
        {
        super.setup(state, base);
                
        // load whether we should set context or not
        shouldSetContext = state.parameters.getBoolean(base.push(P_SHOULD_SET_CONTEXT), null, true);
        
 
		
        Properties prop = getProperties(simstate);
        
        parameterType = new int[prop.numProperties()];
        parameterValue = new double[prop.numProperties()];
        
        // load default parameters
        for(int j = 0; j < parameterType.length; j++)
            {
            if (prop.getType(j) == Double.TYPE)
                {
                parameterType[j] = TYPE_DOUBLE;
                parameterValue[j] = ((Double)(prop.getValue(j))).doubleValue();
                }
            else if (prop.getType(j) == Integer.TYPE)
                {
                parameterType[j] = TYPE_INT;
                parameterValue[j] = (int)(((Integer)(prop.getValue(j))).intValue());
                }
            else if (prop.getType(j) == Boolean.TYPE)
                {
                parameterType[j] = TYPE_BOOLEAN;
                parameterValue[j] = ((((Boolean)(prop.getValue(j))).booleanValue()) == true ? 1.0 : 0.0);
                }
            else
                {
                parameterType[j] = TYPE_OTHER;
                parameterValue[j] = 0.0;
                }
            }
            
        numObjectives = state.parameters.getInt(new ec.util.Parameter("mason-objectives"), null);
        if (numObjectives == -1)
            {
            System.err.println("mason-numobjectives missing or malformed.   Default is 1");
            numObjectives = 1;
            }
        state.parameters.set(new ec.util.Parameter("multi.fitness.num-objectives"), "" + numObjectives);
    	

    	
        //int maximumSteps = base.getInt(new ec.util.Parameter("mason-steps"), null);
        maximumSteps = state.parameters.getInt(new ec.util.Parameter("mason-steps"), null, 0);
        if (maximumSteps <= 0)
        	{
			System.err.println("mason-steps missing or malformed.  Default is 0.");
			maximumSteps = 0;
        	}
        	
        //double maximumTime = base.getDouble(new ec.util.Parameter("mason-time"), null);
        maximumTime = state.parameters.getDouble(new ec.util.Parameter("mason-time"), null, 0.0);
        if (maximumTime <= 0)
        	{
			System.err.println("mason-time missing or malformed.  Default is 0.");
			maximumTime = 0.0;
        	}
        	
        if (maximumSteps == 0 && maximumTime == 0)
        	{
        	System.err.println("mason-steps or mason-time cannot both be missing, malformed, or 0. Bailing.");
        	}
        	
        numTrials = state.parameters.getInt(new ec.util.Parameter("mason-num-trials"), null);
		if (numTrials <= 0) 
			{
			System.err.println("mason-num-trials missing or malformed.  Default is 1.");
			numTrials = 1;
			}
			
        rebuildSimState = state.parameters.getBoolean(new ec.util.Parameter("mason-rebuild-model"), null, false);
        if (!state.parameters.exists(new ec.util.Parameter("mason-rebuild-model"), null))
        	{
			System.err.println("mason-rebuild-model missing or malformeds.  Default is false.");
        	}
     
        /*       		      
        numObjectives = base.getInt(new ec.util.Parameter("multi.fitness.num-objectives"), null);
        if (numObjectives == -1)
            {
            System.err.println("mason-numobjectives missing or malformed.   Default is 1");
            numObjectives = 1;
            base.set(new ec.util.Parameter("multi.fitness.num-objectives"), "" + numObjectives);

            }
    	

        maximumSteps = base.getInt(new ec.util.Parameter("mason-steps"), null, 0);
        if (maximumSteps <= 0)
        	{
			System.err.println("mason-steps missing or malformed.  Default is 0.");
			maximumSteps = 0;
        	}
        	
        //double maximumTime = base.getDouble(new ec.util.Parameter("mason-time"), null);
        maximumTime = base.getDouble(new ec.util.Parameter("mason-time"), null, 0.0);
        if (maximumTime <= 0)
        	{
			System.err.println("mason-time missing or malformed.  Default is 0.");
			maximumTime = 0.0;
        	}
        	
        if (maximumSteps == 0 && maximumTime == 0)
        	{
        	System.err.println("mason-steps or mason-time cannot both be missing, malformed, or 0. Bailing.");
        	}
        	
        numTrials = base.getInt(new ec.util.Parameter("mason-num-trials"), null);
		if (numTrials <= 0) 
			{
			System.err.println("mason-num-trials missing or malformed.  Default is 1.");
			numTrials = 1;
			}
			
        rebuildSimState = base.getBoolean(new ec.util.Parameter("mason-rebuild-model"), null, false);
        if (!base.exists(new ec.util.Parameter("mason-rebuild-model"), null))
        	{
			System.err.println("mason-rebuild-model missing or malformeds.  Default is false.");
        	}
        	
        */

        
        
        }
        
        
    void initSimState(EvolutionState state, int threadnum)
        {
        if (simstate == null || rebuildSimState)
            {
            try
                {
                Class cls = Class.forName(modelClassName);

                try
                    {
                    Constructor cons = cls.getConstructor(new Class[] { Long.TYPE });

                    try
                        {
                        simstate = (SimState)(cons.newInstance(new Object[] { Long.valueOf(50957) }));          // some dummy random number seed
                        }
                    catch (InstantiationException e)
                        {
                        state.output.fatal("Could not instantiate " + modelClassName);
                        }
                    catch (IllegalAccessException e)
                        {
                        state.output.fatal("Could not instantiate " + modelClassName);
                        }
                    catch (InvocationTargetException e)
                        {
                        state.output.fatal("Could not instantiate " + modelClassName);
                        }
                    }
                catch (NoSuchMethodException e)
                    {
                    state.output.fatal("Could not find constructor(long) for " + modelClassName);
                    }

                }
            catch(ClassNotFoundException e)
                {
                state.output.fatal("Could not find class " + modelClassName);
                }
                        
                                
            }
        //simstate.random = state.random[threadnum];            // this is the real generator we'll use
        }
    
    public static Properties getProperties(SimState simstate)
        {
        return Properties.getProperties(simstate, false, false, false, true, true);
        }
    

        	
    void setParameter(EvolutionState state, Properties properties, int index, double value)
        {
        String val = "";
        if (parameterType[index] == TYPE_OTHER)
            {
            state.output.fatal("Invalid type for MASON Problem at index " + index);
            }
        else if (parameterType[index] == TYPE_DOUBLE)
            {
            val = "" + value;
            }
        else if (parameterType[index] == TYPE_INT)
            {
            if (value != (int)value) // uh oh
                {
                state.output.fatal("Value " + value + " is not an integer, as expected in index " + index);
                }
            else
                {
                val = "" + (int)value;
                }
            }
        else if (parameterType[index] == TYPE_BOOLEAN)
            {
            if (value != 1 && value != 0)
                {
                state.output.fatal("Value " + value + " is not 1 or 0, as expected in index " + index);
                }
            else
                {
                val = (value == 1 ? "true" : "false");
                }
            }
        else 
            {
            state.output.fatal("Whaaaaaa?");
            }
        
        properties.setValue(index, val);
        }
        
    //instead of ind, use combinedGenome because we are joining disruptions with safety stocks
	void setProperties(EvolutionState state, double[] combinedGenome)
	    {
	    // We restrict the properties as:
	    // 1. Don't expand collections (we're ignoring those properties anyway)
	    // 2. Don't include SimState as a superclass
	    // 3. Don't include getClass()
	    // 4. Allow domFoo() and hideFoo() extensions
	    // 5. Allow dynamic properties / proxies
	    Properties prop = Properties.getProperties(simstate, false, false, false, true, true);
	    
	    // reload default parameters
	    for(int j = 0; j < parameterType.length; j++)
	        {
	        if (parameterType[j] != TYPE_OTHER)
	            {
	            setParameter(state, prop, j, parameterValue[j]);
	            }
	        }
	    
	    // override with individual parameters

        double[] genome = combinedGenome;

		((DemoCoevolve)simstate).setOptimizationParameters(genome);
        	

        }
        
    void setFitness(EvolutionState state, Individual ind, double[] assessment)
        {
        Fitness fit = ind.fitness;
        
        if (fit instanceof MultiObjectiveFitness)
            {
            MultiObjectiveFitness mf = (MultiObjectiveFitness)fit;
            for(int i = 0; i < mf.maxObjective.length; i++)
                {                       
                mf.maxObjective[i] = 1;
                mf.minObjective[i] = 0;
                mf.maximize[i] = true;
                }
                
            // load the objectives
            mf.setObjectives(state, assessment);
            ind.evaluated = true;
            }
        else if (fit instanceof SimpleFitness)
            {
            SimpleFitness sf = (SimpleFitness)fit;
            sf.setFitness(state, assessment[0], assessment[0] == 1.0);
            ind.evaluated = true;
            }
        else
            {
            state.output.fatal("Unknown Fitness for Pharma3DemoCoevProblem2");
            }
        }
        
	/////coevolution stuff
	
	
	public void preprocessPopulation(EvolutionState state, Population pop, boolean[] prepareForAssessment, boolean countVictoriesOnly) {
			//System.out.println("preprocess has been called");
			
			//System.out.println("prepareForAssess");
			for (boolean a : prepareForAssessment){
			    System.out.println(a);
			}
			System.out.println(pop.subpops.size());
			for(int i = 0; i < pop.subpops.size(); i++) {
				if (prepareForAssessment[i]) {
				
				    System.out.println("pop "+i+" numb of inds: "+pop.subpops.get(i).individuals.size());
					for(int j = 0; j < pop.subpops.get(i).individuals.size(); j++) {
						SimpleFitness fit = (SimpleFitness)(pop.subpops.get(i).individuals.get(j).fitness);
						fit.trials = new ArrayList();
					}
				}
			}
			
			//System.out.println("pre subpop 0 : "+pop.subpops.get(0).individuals.size());
            //System.out.println("pre subpop 1 : "+pop.subpops.get(1).individuals.size());
            //System.out.println("----");
	}

	
	public void evaluate(EvolutionState state, Individual[] ind, boolean[] updateFitness, boolean countVictoriesOnly, int[] subpops, int threadnum) {
			
			System.out.println("here!");
			//System.out.println(ind.length);
			//System.out.println(numTrials); //0
			//System.out.println(numObjectives); //0
			
		    //I don't think we need this, we can go through the ind fitness (which I assume is calculated using assess?
		    double[] genome1 = ((DoubleVectorIndividual)ind[0]).genome;
		    double[] genome2 = ((DoubleVectorIndividual)ind[1]).genome;
			
			//we put both in as our properties
			double[] combinedGenome = new double[genome1.length+ genome2.length];
			
			int combined_ind = 0;
			
			for (int i=0; i<genome1.length; i++){
			    combinedGenome[combined_ind] = genome1[i];
			    combined_ind = combined_ind + 1;
			}
			
			for (int i=0; i<genome2.length; i++){
			    combinedGenome[combined_ind] = genome2[i];
			    combined_ind = combined_ind + 1;
			}
			
			//plug both genomes/inds into DemoCoev (or a special one)
			//see Pharma3DemoCoevProblem2 evaluate for reference
	        double[] results = new double[numObjectives];
	        
	        
	        
	        for(int i = 0; i < numTrials; i++)
	            {
	            initSimState(state, threadnum);
	            setProperties(state, combinedGenome); //implement
	            simstate.start(); 
	                        
	            do
	                {
	                if (!simstate.schedule.step(simstate)) 
	                    {
	                    break; 
	                    }
	                
	                else{
	                System.out.println("stepping...");
	                }
	                }
	            //while(simstate.schedule.getSteps() < maximumSteps); 
	            while((simstate.schedule.getSteps() < maximumSteps) || (simstate.schedule.getTime() < maximumTime));
	            double[] r = simstate.assess(numObjectives);
	            for(int j = 0; j < r.length; j++)
	                results[j] += r[j];

	            simstate.finish();
	            }
	                        
	        for(int j = 0; j < results.length; j++){
	            //System.out.println(results[j]);
	            //System.out.println(numTrials);

	            results[j] /= numTrials;
	            //System.out.println(results[j]);
	            //System.out.println("---");

	            }
	        
	        //set fitness, I'm a little confused here on how this works
	        //setFitness(state, ind, results);
			//Pharma3DemoCoevFitness fit1 = (Pharma3DemoCoevFitness)(ind[0].fitness);
			//Pharma3DemoCoevFitness fit2 = (Pharma3DemoCoevFitness)(ind[1].fitness);		
			SimpleFitness fit1 = (SimpleFitness)(ind[0].fitness);
			SimpleFitness fit2 = (SimpleFitness)(ind[1].fitness);

			
			if (updateFitness[0]) {
				double fitScore = results[0];

                //System.out.println("fit1 : "+fit1);
                //System.out.println("fit1.trials : "+fit1.trials);
                //System.out.println("fit1 class "+fit1.getClass().getSimpleName());
                //System.out.println("fitScore : "+fitScore);

				fit1.trials.add(fitScore); //look into this
				//fit1.setFitness(state, ind[0], results[0]);
				fit1.setFitness(state, fitScore);

			}
			
			if (updateFitness[1]) {
			    double fitScore = -1 * results[0];
				fit2.trials.add(fitScore);
				// set the fitness in case we’re using Single Elimination Tournament
				//fit2.setFitness(state, ind[1], -1 * results[0]); //-1, because we want opposite direction
				fit2.setFitness(state, fitScore); //-1, because we want opposite direction

			}
			
			//for (int s : subpops){
			//System.out.println("subpops : "+s);
			//}
			//System.out.println("subpops : "+subpops);
            //System.out.println("----");
			

		}

    public int postprocessPopulation(final EvolutionState state, Population pop, boolean[] assessFitness, boolean countVictoriesOnly)
        {
        int total = 0;
        for(int i = 0; i < pop.subpops.size(); i++ )
            if (assessFitness[i])
                for(int j = 0; j < pop.subpops.get(i).individuals.size() ; j++ )
                    {
                    SimpleFitness fit = ((SimpleFitness)(pop.subpops.get(i).individuals.get(j).fitness));
                                                                        
                    // we take the max over the trials
                    double max = Double.NEGATIVE_INFINITY;
                    int len = fit.trials.size();
                    for(int l = 0; l < len; l++){
                        
                    
                        max = Math.max((Double)(fit.trials.get(l)), max);  // it'll be the first one, but whatever
                    }
                                        
                    fit.setFitness(state, max);
                    pop.subpops.get(i).individuals.get(j).evaluated = true;
                    total++;
                    }
        //System.out.println("post subpop 0 : "+pop.subpops.get(0).individuals.size());
        //System.out.println("post subpop 1 : "+pop.subpops.get(1).individuals.size());
        //System.out.println("postprocess has been called");

        return total;
        }
				
	
    public void sendAdditionalData(EvolutionState state, DataOutputStream dataOut)
        {
        blockOnReady();
        try
            {
            dataOut.writeUTF(modelClassName);
            dataOut.writeInt(maximumSteps);
            dataOut.writeDouble(maximumTime);
            dataOut.writeInt(numTrials);
            dataOut.writeBoolean(rebuildSimState);
            dataOut.writeBoolean(treatParametersAsArray);
            int size = parameterType.length;
            dataOut.writeInt(size);
            for(int i = 0; i < size; i++)
                {
                //System.err.println("PARAM " + i + " " + " TYPE " + parameterType[i] + " VALUE " + parameterValue[i]);
                dataOut.writeInt(parameterType[i]);
                dataOut.writeDouble(parameterValue[i]);
                }
            dataOut.writeInt(fitnessType);
            dataOut.writeInt(numObjectives);
            size = indParameterIndex.length;
            dataOut.writeInt(size);
            for(int i = 0; i < size; i++)
                {
                dataOut.writeInt(indParameterIndex[i]);
                }
            }
        catch (IOException e)
            {
            state.output.fatal("IOException in sending data");
            }
        }

    public void receiveAdditionalData(EvolutionState state, DataInputStream dataIn)
        {
        try
            {
            modelClassName = dataIn.readUTF();
            maximumSteps = dataIn.readInt();
            maximumTime = dataIn.readDouble();
            numTrials = dataIn.readInt();
            rebuildSimState = dataIn.readBoolean();
            treatParametersAsArray = dataIn.readBoolean();
            int size = dataIn.readInt();
            parameterType = new int[size];
            parameterValue = new double[size];
            for(int i = 0; i < size; i++)
                {
                parameterType[i] = dataIn.readInt();
                parameterValue[i] = dataIn.readDouble();
                //System.err.println("PARAM " + i + " " + " TYPE " + parameterType[i] + " VALUE " + parameterValue[i]);
                }
            fitnessType = dataIn.readInt();
            numObjectives = dataIn.readInt();
            size = dataIn.readInt();
            indParameterIndex = new int[size];
            for(int i = 0; i < size; i++)
                {
                indParameterIndex[i] = dataIn.readInt();
                }

//            System.err.println("FITNESS TYPE " + fitnessType);
/*
            if (fitnessType == FITNESS_TYPE_NSGA2)
            	{
//            	System.err.println("SETTING UP SPECIES AGAIN");
	            state.parameters.set(new ec.util.Parameter("pop.subpop.0.species.fitness"), "ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness");
	            state.population.subpops.get(0).species.setup(state, new ec.util.Parameter("pop.subpop.0.species"));
	            }
*/
            }
        catch (IOException e)
            {
            state.output.fatal("IOException in sending data");
            }
        
        }

    public void transferAdditionalData(EvolutionState state)
        {
        // we don't know if state has a MasterProblem or not.
        Problem problem = state.evaluator.p_problem;
        if (problem instanceof MasterProblem)  // maybe this will never happen?  Dunno FIXME
            {
            problem = ((MasterProblem)problem).problem;
            }
                
        if (problem instanceof Pharma3DemoCoevProblem2)
            {
            Pharma3DemoCoevProblem2 mp = (Pharma3DemoCoevProblem2)problem;
            mp.parameterType = (int[])(parameterType.clone());
            mp.parameterValue = (double[])(parameterValue.clone());
            mp.indParameterIndex = (int[])(indParameterIndex.clone());
            mp.simstate = null;
            mp.maximumSteps = maximumSteps;
            mp.maximumTime = maximumTime;
            mp.numTrials = numTrials;
            mp.modelClassName = modelClassName;
            mp.numObjectives = numObjectives;
            mp.rebuildSimState = rebuildSimState;
            mp.treatParametersAsArray = treatParametersAsArray;
            mp.fitnessType = fitnessType;
            
//            System.err.println("FITNESS TYPE " + fitnessType);
            if (fitnessType == FITNESS_TYPE_NSGA2)
            	{
//            	System.err.println("SETTING UP SPECIES AGAIN");
	            state.parameters.set(new ec.util.Parameter("pop.subpop.0.species.fitness"), "ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness");
	            state.population.subpops.get(0).species.setup(state, new ec.util.Parameter("pop.subpop.0.species"));
	            }
            }
        else
            {
            // uh oh
            state.output.fatal("Not a MASON Problem: " + problem);
            }
        }
        	
	



    }
