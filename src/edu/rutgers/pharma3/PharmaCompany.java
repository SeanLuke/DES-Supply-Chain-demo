package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** Models the PharmaCompany management office. It receives orders for drugs from HospitalPool.
    The "Delay" functionality corresponds to the order's travel from the hospital pool 
    to the pharma company.

    The PharmaCompany is a "Sink" for orders.
 */
public class PharmaCompany extends Sink // Delay
    implements //Receiver, Named,
 Reporting {

    /** An order paper */
    static CountableResource drugOrderResource = new CountableResource("DrugOrder",0);
    /** Resources to be ordered from external suppliers */
    static CountableResource rawMaterial = new CountableResource("RawMaterial",0),
	pacMaterial = new CountableResource("PackagingMaterial",0),
	excipient = new CountableResource("Excipient",0);

    static CountableResource api = new CountableResource("API",0);
    static CountableResource bulkDrug = new CountableResource("bulkDrug",0);
    //static CountableResource pacDrug = new CountableResource("packagedDrug",0);

    MaterialSupplier rawMatSupplier, pacMatFacility, excipientFacility;
    public MaterialSupplier getRawMatSupplier() { return rawMatSupplier; }
    public MaterialSupplier getExcipientFacility() { return excipientFacility; }
    public MaterialSupplier getPacMatFacility() { return pacMatFacility; }

    private Delay orderDelay;
    Production apiProduction, drugProduction, packaging;

    public Production getApiProduction() {return apiProduction;    }
    public Production getDrugProduction() {	return drugProduction;    }
    public Production getPackaging() {	return packaging;    }

    Distributor distro;
    public Distributor getDistributor() {return distro;    }
    
    //    MSink dongle; 
    PharmaCompany(SimState state, String name, Config config, HospitalPool hospitalPool, Batch pacDrugBatch) throws IllegalInputException {
	super(state, drugOrderResource);
	setName(name);


	//Batch pacDrugBatch  = (Batch)hospitalPool.getTypical();
	//System.out.println("pacDrugBatch = " + pacDrugBatch  );
	
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");



	
	orderDelay = new Delay( state, drugOrderResource);
	orderDelay.setDelayDistribution(para.getDistribution("orderDelay",state.random));
	orderDelay.addReceiver(this);

	Batch rawMatBatch = new Batch(rawMaterial), pacMatBatch = new Batch(pacMaterial), excipientBatch = new Batch(excipient);
	rawMatSupplier = new MaterialSupplier(state, "RawMaterialSupplier", config, rawMatBatch);
	pacMatFacility = new MaterialSupplier(state, "PacMatSupplier", config, 	pacMatBatch);
	excipientFacility = new MaterialSupplier(state, "ExcipientSupplier", config, excipientBatch);


	Batch apiBatch = new Batch(api),  bulkDrugBatch = new Batch( bulkDrug);

	apiProduction = new Production(state, "ApiProduction",  config,
				       new Batch[] {rawMatBatch}, apiBatch);
	rawMatSupplier.setQaReceiver(apiProduction.getEntrance(0));

	
	drugProduction = new Production(state, "DrugProduction",  config,
					new Batch[] {apiBatch, excipientBatch}, bulkDrugBatch);
	apiProduction.setQaReceiver(drugProduction.getEntrance(0));
	excipientFacility.setQaReceiver(drugProduction.getEntrance(1));


	packaging = new Production(state, "Packaging",  config,
					new Batch[] {bulkDrugBatch, pacMatBatch}, pacDrugBatch);
	drugProduction.setQaReceiver(packaging.getEntrance(0));
	pacMatFacility.setQaReceiver(packaging.getEntrance(1));

	distro = new Distributor(state, "Distributor", config,  pacDrugBatch);
	packaging.setQaReceiver(distro);	
	distro.setDeliveryReceiver(hospitalPool);
	
    	//dongle = new MSink(state,pacDrug);
	//packaging.setQaReceiver(dongle);	

	state.schedule.scheduleRepeating(apiProduction);
	state.schedule.scheduleRepeating(drugProduction);
	state.schedule.scheduleRepeating(packaging);
	state.schedule.scheduleRepeating(distro);

	
    }

    /** Overriding accept(), so that we can process the receipt of an order.
	FIXME: the assumption is made that to produce 1 unit of drug, one needs 1 unit of each 
	input. This can be changed if needed.
	@param amount An "order paper" (CountableResource)
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
	Vector<String> v= new Vector<>();
	v.add( 	rawMatSupplier.report());
	v.add( 	pacMatFacility.report());
	v.add( 	excipientFacility.report());
	v.add( 	apiProduction.report());
	v.add( 	drugProduction.report());
	v.add(  packaging.report());
	v.add( 	distro.report());
	//v.add( 	dongle.report());
	return String.join("\n", v);
    } 
}
