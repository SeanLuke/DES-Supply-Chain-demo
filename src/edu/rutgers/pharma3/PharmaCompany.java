package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;
import sim.des.portrayal.*;

import edu.rutgers.util.*;

/** Models the PharmaCompany management office. It receives orders for drugs from HospitalPool.
    The "Delay" functionality corresponds to the order's travel from the hospital pool 
    to the pharma company.

    The PharmaCompany is a "Sink" for orders.
 */
public class PharmaCompany extends Sink 
    implements //Receiver, Named,
 Reporting {

    /** An order paper */
    static CountableResource drugOrderResource = new CountableResource("DrugOrder",0);
    /** Resources to be ordered from external suppliers */
    static CountableResource rawMaterial = new CountableResource("RawMaterial",0),
	pacMaterial = new CountableResource("PackagingMaterial",0),
	excipient = new CountableResource("Excipient",0);

    /** Resources produced inside the supply chain */
    static CountableResource api = new CountableResource("Api",0);
    static CountableResource bulkDrug = new CountableResource("BulkDrug",0);

    /** Supply chain units that model external suppliers of the input
	materials */
    MaterialSupplier rawMatSupplier, pacMatFacility, excipientFacility;
    public MaterialSupplier getRawMatSupplier() { return rawMatSupplier; }
    public MaterialSupplier getExcipientFacility() { return excipientFacility; }
    public MaterialSupplier getPacMatFacility() { return pacMatFacility; }

    //    private Delay orderDelay;

    /** Production units wihin our supply chain */
    Production apiProduction, drugProduction, packaging;
    //Production cmoApiProduction, cmoDrugProduction, cmoPackaging;
    Production[] cmoTrack=new Production[4];

    
    public Production getApiProduction() {return apiProduction;    }
    public Production getDrugProduction() {	return drugProduction;    }
    public Production getPackaging() {	return packaging;    }

    Distributor distro;
    public Distributor getDistributor() {return distro;    }

    /** Splitters are elements used to "split" the output of one unit to two
	destinations.
     */	
    //    Splitter rawMatSplitter, apiSplitter, drugSplitter, cmoApiSplitter;

    /** This was used during development instead of not-yet-built
	parts of the supply chain model */
    //    MSink dongle; 

    final double fudgeFactor;

    final GraphAnalysis ga;

    /** This is used in GraphAnalysis to identify "main" inputs of
	Production nodes */
    final Batch[] theMainChainOfResources;

    
    PharmaCompany(SimState state, String name, Config config, Pool hospitalPool, Batch pacDrugBatch) throws IllegalInputException, IOException {
	super(state, drugOrderResource);
	setName(name);

	ParaSet para = config.get(name);

	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	
	//	orderDelay = new Delay( state, drugOrderResource);
	//	orderDelay.setDelayDistribution(para.getDistribution("orderDelay",state.random));
	//	orderDelay.addReceiver(this);


	//fudgeFactor = para.getDouble("fudgeFactor", 1.0);
	

	
	// Raw material comes in lots, as it has expiration dates
	rawMatSupplier = MaterialSupplier.mkMaterialSupplier(state, "RawMaterialSupplier", config, rawMaterial, true);
	Batch rawMatBatch = (Batch)rawMatSupplier.getPrototype();

	// Packaging material is fungible (no expiration); thus, no lots
	pacMatFacility =  MaterialSupplier.mkMaterialSupplier(state, "PacMatSupplier", config, pacMaterial, false);
	
	// Excipient comes in lots, as it has expiration dates
	excipientFacility = MaterialSupplier.mkMaterialSupplier(state, "ExcipientSupplier", config, excipient, true);
	Batch excipientBatch = (Batch)excipientFacility.getPrototype();
	
	Batch apiBatch = Batch.mkPrototype(api, config),
	    bulkDrugBatch= Batch.mkPrototype(bulkDrug, config);


	apiProduction = new Production(state, "ApiProduction",  config,
				       new Batch[] {rawMatBatch}, apiBatch);

	drugProduction = new Production(state, "DrugProduction",  config,
					new Batch[]{ apiBatch, excipientBatch}, bulkDrugBatch);

	packaging = new Production(state, "Packaging",  config,
				   new Resource[] {bulkDrugBatch, pacMaterial}, pacDrugBatch);

	rawMatSupplier.sm.setQaReceiver(apiProduction.getEntrance(0), 0.90);

	       
	apiProduction.sm.setQaReceiver(drugProduction.getEntrance(0), 0.70);

	
	excipientFacility.sm.setQaReceiver(drugProduction.getEntrance(1));
	
   	drugProduction.sm.setQaReceiver(packaging.getEntrance(0), 0.50);
	
	pacMatFacility.sm.setQaReceiver(packaging.getEntrance(1));

	distro = new Distributor(state, "Distributor", config,  pacDrugBatch);
	packaging.sm.setQaReceiver(distro);	

	((Demo)state).add(apiProduction);
	((Demo)state).add(drugProduction);
	state.schedule.scheduleRepeating(packaging);

	((Demo)state).add(distro);

	// the suppliers are scheduled just to enable charting
	((Demo)state).add(rawMatSupplier );
	state.schedule.scheduleRepeating(pacMatFacility);
	state.schedule.scheduleRepeating(excipientFacility);

	setupCmoTracks((Demo)state,  config);

	Vector<Production> vp = Util.array2vector(cmoTrack);
	Production [] myp = {apiProduction, drugProduction, packaging};
	vp.addAll( Util.array2vector(myp));

	theMainChainOfResources = new Batch[] {rawMatBatch, apiBatch, bulkDrugBatch, pacDrugBatch};
	    
		    
	ga = new GraphAnalysis(rawMatSupplier, distro, vp.toArray(new Production[0]), theMainChainOfResources);

	double gamma =rawMatSupplier.computeGamma(); 
	fudgeFactor = 1.0 / ga.totalTerminalAmt() / gamma;
	System.out.println(name + ": RM over-ordering factor=" + fudgeFactor);

	for(Production p: myp) { p.setPlan(0); }
    }

    /** Sets up the 4 CMO Tracks, based on the data in the config file */
    private void setupCmoTracks(Demo demo, Config config)  throws IllegalInputException, IOException {
	for(int j=0; j<cmoTrack.length; j++) {
	    char c = (char)('A' +j);
	    String name = "CmoTrack" + c;
	    ParaSet para = config.get(name);

	    //CmoTrackA,input,RawMaterialSupplier,0.05
	    
	    Vector<String> v = para.get("input");
	    if (v.size()!=2) throw new IllegalArgumentException("Invalid data for " +name+ ".input");
	    Steppable node = demo.lookupNode(v.get(0));
	    if (node==null) throw new IllegalArgumentException("Invalid input name for " +name+ ".input: " + v.get(0));
	    SplitManager.HasQA inputNode = (SplitManager.HasQA) node;
	    double fraction = Double.parseDouble(v.get(1));
	    QaDelay inputQaDelay = inputNode.getQaDelay();

	    // CmoTrackA,output,DrugProduction
	    v = para.get("output");
	    if (v.size()!=1) throw new IllegalArgumentException("Invalid data for " +name+ ".output");
	    Steppable node2 = demo.lookupNode(v.get(0));
	    if (node2==null) throw new IllegalArgumentException("Invalid output name for " +name+ ".input: " + v.get(0));
	    Receiver rcv = null;
	    Batch outResource;
	    if (node2 instanceof Pool) {
		rcv = (Pool)node2;
		outResource = (Batch)((Pool)rcv).prototype;
	    } else if (node2 instanceof Production) {
		// just assuming input buffer 0
		rcv = ((Production)node2).getEntrance(0);
		outResource = (Batch)((Production)node2).inResources[0];
	    } else  throw new IllegalArgumentException("Cannot identify the output unit as given for " +name+ ".input: " + v.get(0));
	    
		    
	    cmoTrack[j] = new Production(demo, name,  config,
					 new Resource[] {inputQaDelay.prototype},
					 outResource);

	    inputNode.setQaReceiver(cmoTrack[j].getEntrance(0), fraction);
	    cmoTrack[j].setQaReceiver(rcv, 1.0);
	    demo.schedule.scheduleRepeating(cmoTrack[j]);
		
	}
    }
    

    // (x,y)
    void depict(DES2D field) {
	if (field==null) return;
	
	rawMatSupplier.depict(field, 20, 20);
	excipientFacility.depict(field,  20, 220);
	pacMatFacility.depict(field,  20, 420);

	//field.add(rawMatSplitter, 200, 50);


	
	apiProduction.depict(field, 400, 20);
 
	//field.add(apiSplitter, 500, 50);

	drugProduction.depict(field, 600, 120);
	packaging.depict(field, 800, 220);

	//field.add(drugSplitter, 700, 150);

	for(int j=0; j<cmoTrack.length; j++) {
	    cmoTrack[j].depict(field, 400, 100+50*j);
	}
	
	//	cmoApiProduction.depict(field, 400, 350);
	//field.add(cmoApiSplitter, 500, 380);
	
	//cmoDrugProduction.depict(field, 600, 450);
	//cmoPackaging.depict(field, 800, 550);

	field.add(distro, 900, 300);

    }


    /** Overriding accept(), so that we can process the receipt of an order.
	FIXME: the assumption is made that to produce 1 unit of drug, one needs 1 unit of each 
	input. This can be changed if needed.
	@param amount An "order paper" (CountableResource). Amount in units.
     */
    public boolean accept(Provider provider, Resource amount, double atLeast, double atMost) {
	//double s0=getAvailable();

	String msg = "At t=" + state.schedule.getTime() + ", " +  getName()+ " acting on order for supply of "+
	    atLeast + " to " +  atMost + " units of " + amount; // +
	    //", while qa.ava=" + qaDelay.getAvailable();

	double amt = amount.getAmount();

	//distro.addToPlan(amt);
	
	boolean z = super.accept(provider, amount, atLeast, atMost);
	//double s=getAvailable();
	//msg += "; stock changed from " + s0 + " to " +s;	
	if (Demo.verbose) System.out.println(msg);

	rawMatSupplier.receiveOrder(Math.round(amt * fudgeFactor));

	pacMatFacility.receiveOrder(Math.round(amt * ga.getStartPlanFor(packaging) /pacMatFacility.computeGamma()));

	excipientFacility.receiveOrder(Math.round(amt * ga.getStartPlanFor(drugProduction)/ excipientFacility.computeGamma()));

	Production [] myp = {apiProduction, drugProduction, packaging};
	for(Production p: myp) {
	    double plan = Math.round(amt * ga.getStartPlanFor(p));
	    p.addToPlan(plan);
	}


	/*
	vp.addAll( Util.array2vector(myp));
		    
	GraphAnalysis ga = new GraphAnalysis(rawMatSupplier, distro, vp.toArray(new Production[0]));

	fudgeFactor = 1.0 / ga.terminalAmount / rawMatSupplier.getQaDelay().computeAlpha();
	System.out.println(name + ": RM over-ordering fudgeFactor=" + fudgeFactor);

	for(Production p: myp) { p.setPlan(0); }
	*/

	
	return z;

    }

    public String report() {
	String sep =  "----------------------------------------------------------------------";	   
	Vector<String> v= new Vector<>();
	v.add( "---- Suppliers: -------------------------------");
	v.add( 	rawMatSupplier.report());
	//v.add( 	rawMatSplitter.report());
	v.add( 	pacMatFacility.report());
	v.add( 	excipientFacility.report());
	v.add( "---- FC: --------------------------------------");
	v.add( 	apiProduction.report());
	v.add( 	drugProduction.report());
	v.add(  packaging.report());
	v.add( "---- CMO: -------------------------------------");
	for(int j=0; j<cmoTrack.length; j++) {
	    v.add( cmoTrack[j].report());
	}

	//v.add( 	cmoApiProduction.report());
	//v.add( 	cmoDrugProduction.report());
	//v.add(  cmoPackaging.report());
	v.add( sep);
	v.add( 	distro.report());
	//v.add( 	dongle.report());
	return String.join("\n", v);
    }

    /** The list of Macro objects, for help in the GUI */
    Macro[] listMacros() {
	return new Macro[] {
	    rawMatSupplier,
	    excipientFacility,
	    pacMatFacility,
	    apiProduction,
	    drugProduction,
	    packaging,
	    //cmoApiProduction,
	    //cmoDrugProduction,
	    //cmoPackaging
	};
    }
    
}
