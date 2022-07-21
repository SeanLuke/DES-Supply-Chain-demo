package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
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
    static CountableResource api = new CountableResource("API",0);
    static CountableResource bulkDrug = new CountableResource("bulkDrug",0);
    //static CountableResource pacDrug = new CountableResource("packagedDrug",0);


    /** Supply chain units that model external suppliers of the input
	materials */
    MaterialSupplier rawMatSupplier, pacMatFacility, excipientFacility;
    public MaterialSupplier getRawMatSupplier() { return rawMatSupplier; }
    public MaterialSupplier getExcipientFacility() { return excipientFacility; }
    public MaterialSupplier getPacMatFacility() { return pacMatFacility; }

    //    private Delay orderDelay;

    /** Production units wihin our supply chain */
    Production apiProduction, drugProduction, packaging;
    Production cmoApiProduction, cmoDrugProduction, cmoPackaging;

    public Production getApiProduction() {return apiProduction;    }
    public Production getDrugProduction() {	return drugProduction;    }
    public Production getPackaging() {	return packaging;    }

    Distributor distro;
    public Distributor getDistributor() {return distro;    }

    /** Splitters are elements used to "split" the output of one unit to two
	destinations.
     */	
    Splitter rawMatSplitter, apiSplitter, drugSplitter, cmoApiSplitter;

    /** This was used during development instead of not-yet-built
	parts of the supply chain model */
    //    MSink dongle; 


    PharmaCompany(SimState state, String name, Config config, HospitalPool hospitalPool, Batch pacDrugBatch) throws IllegalInputException, IOException {
	super(state, drugOrderResource);
	setName(name);
	ParaSet para = config.get(name);

	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	
	//	orderDelay = new Delay( state, drugOrderResource);
	//	orderDelay.setDelayDistribution(para.getDistribution("orderDelay",state.random));
	//	orderDelay.addReceiver(this);


	// pacMatBatch = new Batch(pacMaterial)
	rawMatSupplier = MaterialSupplier.mkMaterialSupplier(state, "RawMaterialSupplier", config, rawMaterial, true);
	Batch rawMatBatch = (Batch)rawMatSupplier.getPrototype();
	
	pacMatFacility =  MaterialSupplier.mkMaterialSupplier(state, "PacMatSupplier", config, pacMaterial, false);
	
	excipientFacility = MaterialSupplier.mkMaterialSupplier(state, "ExcipientSupplier", config, excipient, true);
	Batch excipientBatch = (Batch)excipientFacility.getPrototype();
	
	Batch apiBatch = Batch.mkPrototype(api, config.get( "ApiProduction")),
	    bulkDrugBatch = Batch.mkPrototype(bulkDrug, config.get( "DrugProduction"));


	apiProduction = new Production(state, "ApiProduction",  config,
				       new Batch[] {rawMatBatch}, apiBatch);
	cmoApiProduction = new Production(state, "CmoApiProduction",  config,
				       new Batch[] {rawMatBatch}, apiBatch);

	drugProduction = new Production(state, "DrugProduction",  config,
					new Batch[]{ apiBatch, excipientBatch}, bulkDrugBatch);
	cmoDrugProduction = new Production(state, "CmoDrugProduction",  config,
					new Batch[]{ apiBatch}, bulkDrugBatch);

	packaging = new Production(state, "Packaging",  config,
				   new Resource[] {bulkDrugBatch, pacMaterial}, pacDrugBatch);
	cmoPackaging = new Production(state, "CmoPackaging",  config,
				      new Resource[] {bulkDrugBatch}, pacDrugBatch);

	rawMatSupplier.setQaReceiver(rawMatSplitter = new Splitter( state, rawMatBatch));	
	rawMatSplitter.addReceiver(apiProduction.getEntrance(0), 90);
	rawMatSplitter.addReceiver(cmoApiProduction.getEntrance(0), 10);

		
	apiProduction.setQaReceiver(apiSplitter = new Splitter( state, apiBatch));	
	apiSplitter.addReceiver(drugProduction.getEntrance(0), 70);
	apiSplitter.addReceiver(cmoDrugProduction.getEntrance(0), 30);

	cmoApiProduction.setQaReceiver(cmoApiSplitter = new Splitter( state, apiBatch));	
	cmoApiSplitter.addReceiver(cmoDrugProduction.getEntrance(0), 50);
	cmoApiSplitter.addReceiver(drugProduction.getEntrance(0), 50);
	
	excipientFacility.setQaReceiver(drugProduction.getEntrance(1));
	
       	drugProduction.setQaReceiver(drugSplitter = new Splitter( state, bulkDrugBatch));	
	drugSplitter.addReceiver( packaging.getEntrance(0), 50);
	drugSplitter.addReceiver( cmoPackaging.getEntrance(0), 50);

	cmoDrugProduction.setQaReceiver(cmoPackaging.getEntrance(0));
	
	pacMatFacility.setQaReceiver(packaging.getEntrance(1));

	distro = new Distributor(state, "Distributor", config,  pacDrugBatch);
	packaging.setQaReceiver(distro);	
	cmoPackaging.setQaReceiver(distro);	
	distro.setDeliveryReceiver(hospitalPool);
	
    	//dongle = new MSink(state,pacDrug);
	//packaging.setQaReceiver(dongle);	

	state.schedule.scheduleRepeating(apiProduction);
	state.schedule.scheduleRepeating(drugProduction);
	state.schedule.scheduleRepeating(packaging);

	state.schedule.scheduleRepeating(cmoApiProduction);
	state.schedule.scheduleRepeating(cmoDrugProduction);
	state.schedule.scheduleRepeating(cmoPackaging);

	state.schedule.scheduleRepeating(distro);

	// the suppliers are scheduled just to enable charting
	state.schedule.scheduleRepeating(rawMatSupplier );
	state.schedule.scheduleRepeating(pacMatFacility);
	state.schedule.scheduleRepeating(excipientFacility);
	
    }


    // (x,y)
    void depict(DES2D field) {
	
	rawMatSupplier.depict(field, 20, 20);
	excipientFacility.depict(field,  20, 220);
	pacMatFacility.depict(field,  20, 420);

	field.add(rawMatSplitter, 200, 50);


	
	apiProduction.depict(field, 400, 20);
 
	field.add(apiSplitter, 500, 50);

	drugProduction.depict(field, 600, 120);
	packaging.depict(field, 800, 220);

	field.add(drugSplitter, 700, 150);


	
	cmoApiProduction.depict(field, 400, 350);
	field.add(cmoApiSplitter, 500, 380);
	
	cmoDrugProduction.depict(field, 600, 450);
	cmoPackaging.depict(field, 800, 550);

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

	distro.addToPlan(amt);
	
	boolean z = super.accept(provider, amount, atLeast, atMost);
	//double s=getAvailable();
	//msg += "; stock changed from " + s0 + " to " +s;	
	if (Demo.verbose) System.out.println(msg);
	
	rawMatSupplier.receiveOrder(amt);
	pacMatFacility.receiveOrder(amt);
	excipientFacility.receiveOrder(amt);
 
	return z;

    }

    public String report() {
	String sep =  "----------------------------------------------------------------------";	   
	Vector<String> v= new Vector<>();
	v.add( "---- Suppliers: -------------------------------");
	v.add( 	rawMatSupplier.report());
	v.add( 	rawMatSplitter.report());
	v.add( 	pacMatFacility.report());
	v.add( 	excipientFacility.report());
	v.add( "---- FC: --------------------------------------");
	v.add( 	apiProduction.report());
	v.add( 	drugProduction.report());
	v.add(  packaging.report());
	v.add( "---- CMO: -------------------------------------");
	v.add( 	cmoApiProduction.report());
	v.add( 	cmoDrugProduction.report());
	v.add(  cmoPackaging.report());
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
	    cmoApiProduction,
	    cmoDrugProduction,
	    cmoPackaging
	};
    }
    
}
