package  edu.rutgers.pharma3;

import java.util.*;
import java.io.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
import sim.des.*;

import edu.rutgers.util.*;
import edu.rutgers.pharma3.Splitter.RData;
import edu.rutgers.pharma3.SplitManager.HasQA;
	
/** This class exists so that we can figure in advance what would happen with a given amount (say, 1000 units) of RM that enters the system. Assuming that everything works as designed (with the split ratios, fault rates, etc, already in the system), how much of this RM will eventually arrive to the DC in the form of this finished product? On the way there, how much will pass through each production node? 

We use these numbers to compute the amount of RM that needs to be ordered to produced a desired amount of the finished product. Additionally, we also use these numbers to provide intelligent work orders to production nodes that need orders (because they have safety stocks, and cannot be controlled just by the RM supply).
 */

public class GraphAnalysis {

    private final Distributor distro;
    
    static private DecimalFormat df = new DecimalFormat("0.00#");
	
    /** A Node typically represents a Production unit of the supply chain.
	Additionally, we also have a Node object for the root of the 
	production network, representing the RM entering the system.	
     */
    class Node {
	/** The sequential number of node (0-based) in our table of all nodes.
	    The root node is special, and had -1 in this field.
	*/
	int no;
	// Production element;
	Production.Perfo perfo;
	/** If true, this node feeds to DC */
	boolean terminal = false;
	double terminalAmt = 0;
	//double alpha, beta;
	/** Output/input ratio of this node. Typically, it's computed
	    as (1-alpha-beta) / (1-beta), where alpha is the fault rate,
	    and beta is the rework rate.
	 */
	//double gamma;
	double backlog = 0, bad=0;
	Double utilization = null;
	/** To which other Nodes this Node feeds. The map maps the integer ID
	    of each destsination node to an RData structure containing
	    information on what fraction of the input goes to that destination
	    node.    For a terminal node,
	    this map is empty */
	HashMap<Integer,RData> outputs = new HashMap<>();
	/** How much of a 1.000 of the RM that enters the root will
	    reach the input of that node thru each channel */
	HashMap<Integer,Double> inputAmt = new HashMap<>();
	private double totalInputAmt() {
	    double sum = 0;
	    for(Double x: inputAmt.values()) {
		sum += x;
	    }
	    return sum;
	}

	/** Builds the Node from a Production element or (for the root node
	    only) from the rawMaterialSupplier. 

	    @param x Either a fully-configured Production element, or
	    rawMaterialSupplier. It should have all its outputs etc
	    already properly set.
	 */
	Node(//Production x
	     HasQA _x, int _no) {
	    no = _no;
	    Production x = (_x instanceof Production)? (Production)_x: null;
	    double abg[] = {0,0,1};
	    if (x!=null) {
		//element = x;
		//abg = x.computeABG();


		perfo = new Production.Perfo(x,  theMainChainOfResources);

		
	    } else { // the root node
		//element = null;
		perfo = new Production.Perfo();
	    }
	    //alpha = abg[0];
	    //beta = abg[1];
	    //gamma = abg[2];
	    ArrayList<Receiver> rr = _x.getQaDelay().getReceivers();
	    if (rr.size()!=1) throw new IllegalArgumentException("QA is expected to have exactly 1 receiver");
	    Receiver _r = rr.get(0);
	    if (_r == distro) terminal = true;
	    else if (_r instanceof Production) {
		Production r = (Production)_r;
		outputs.put(prod2no(r), new RData(1.0));
	    } else if (_r instanceof InputStore) {
		Production r = ((InputStore)_r).whose;
		outputs.put(prod2no(r), new RData(1.0));
	    } else if (_r instanceof Splitter) {
		Splitter s  = (Splitter)_r;
		double sf = s.computeSumF(s.data.keySet());
		if (sf == 0) throw  new IllegalArgumentException("All fractions are 0");
		for(Receiver _z: s.data.keySet()) {
		    if (_z instanceof InputStore) {
			Production z = ((InputStore)_z).whose;
			RData q = s.data.get(_z);
			outputs.put(prod2no(z), new RData(q.fraction/sf));
		    } else throw new IllegalArgumentException("Splitter ought to feed to Production nodes only");
		}
				 
	    } else throw  new IllegalArgumentException("Unexpected type for receiver: " + _r.getName());
	}

	String report() {	    
	    String s = (perfo.production==null)? "Root": perfo.production.getName();
	    s += ": in=" + fa(totalInputAmt()) +
		", gamma=" +df.format(perfo.gamma);
	    if (backlog>0) s+= ". Backlog=" + fa(backlog);
	    if (utilization!=null) s+= ". Util=" + df.format(utilization*100) + "%";
	    if (bad>0) s+= ". Bad=" + fa(bad);
	    s += ". Send: ";
	    if (terminal) s += "to Distributor: " + fa(terminalAmt);
	    else {	    
		Vector<String> v = new Vector<>();
		for(int j: outputs.keySet()) {
		    RData d = outputs.get(j);
		    v.add("to " +allProd[j].getName()+ " "+ fa(d.given)+
			  " ("+fpc(d.fraction)+")");
		}
		s += String.join("; ", v);
	    }
	    return s;				
	}
	
    }

    /** How much of a 1.0 quantity of RM at the input of the network reaches
	the terminal (the DC), in the form of finished product. */
    public HashMap<Integer,Double> terminalAmt = new  HashMap<>();

    /** The total amount of stuff arriving to the DC from all sources */
    double totalTerminalAmt() {
	double sum = 0;
	for(Double x: terminalAmt.values()) {
	    sum += x;
	}
	return sum;
    }

    /** The main method for the analysis */
    void doAnalyze(double input, boolean thruputConstrained, boolean useSafety){
	root.inputAmt.put(-1, input);
	analyze(root, thruputConstrained, useSafety);
    }

    
    /** The recursive analysis function. Starting from a particular
	node (the "root" of a subgraph), recursively updates the
	numbers in all nodes reached from this node.
     */
    private void analyze(Node root, boolean thruputConstrained,
			 boolean useSafety		 ) {
	double inAmt = root.totalInputAmt();
	double inAmtUsed = inAmt;
	
	if (thruputConstrained && root.perfo.production!=null) {
	    // how much of the input goes through?
	    double maxIn = root.perfo.thruput * (1-root.perfo.beta);
	    double maxOut = root.perfo.thruput * (1-root.perfo.beta - root.perfo.alpha);
	    inAmtUsed = Math.min( inAmt, maxIn);
	    root.backlog = 	inAmt - inAmtUsed;
	    root.utilization  = inAmtUsed /maxIn;
	} 
	
	double outAmt = inAmtUsed * root.perfo.gamma;
	root.bad = inAmtUsed * (1 - root.perfo.gamma);

	if (root.terminal) {
	    double sent = outAmt;
	    root.terminalAmt = sent;
	    terminalAmt.put(root.no, sent);
	    return;
	}
	for(int j: root.outputs.keySet()) {
	    RData d = root.outputs.get(j);
	    Node y = allNodes[j];
	    double sent = outAmt  * d.fraction;
	    y.inputAmt.put(root.no, sent);
	    d.given = sent;
	    analyze(y, thruputConstrained, useSafety);
	}
    }

    private final Production[] allProd;
    private Node[] allNodes;
    
    private int prod2no(Production p) {
	for(int j=0; j<allProd.length; j++) {
	    if (allProd[j]==p) return j;
	}
	throw new IllegalArgumentException("Production element not registered: " + p);
    }

    /** Used to control formatting of drug amounts in reporting */
    private boolean useBatch=false;

    private String report(boolean _useBatch) {
	useBatch = _useBatch;
	Vector<String> v = new Vector<>();
	if (useBatch) {
	    v.add("Below, 1 ba = " + (long)Math.round(batchSize));
	}
	v.add(root.report());
	for(int j=0; j<allProd.length; j++) {
	    v.add("["+j+"] " + allNodes[j].report());
	}
	v.add("Distributor receives " + fa(totalTerminalAmt()));
	return String.join("\n", v);	
    }

   
    
    /** Format an amount of product */
    private String fa(double x) {
	if (useBatch) {
	    return df.format(x/batchSize) + " ba";
	} else {
	    return df.format(x);
	}	
    }


    boolean almostInt(double x) {
	return Math.abs( x - Math.round(x)) < 1e-6;
    }
    
    /** Converts a number to a percentage, and prints it appropriately */
    private String fpc(double x) {
	double pc = x*100;
	return (almostInt(pc)? "" + Math.round(pc)  : df.format(pc)) + "%";
    }
    
    /** If the output of the entire chain is 1.0, what should 
	be the number of units started by the specified production node?
    */
    double getStartPlanFor(Production p) {
	int j = prod2no(p);
	return allNodes[j].totalInputAmt() / totalTerminalAmt();
    }


    private Batch[] theMainChainOfResources;
    private double batchSize;

    /** Stands for the root of the entire production graph */
    private final Node root;
    
    /**
       @param rawMatSupplier The root node of the production network being analyzed
       @param _distro the DC
       @param _theMainChainOfResources Used to identify "main" resources
     */
    GraphAnalysis(MaterialSupplier rawMatSupplier, Distributor _distro, Production[] vp, Batch[] _theMainChainOfResources) {
	batchSize = rawMatSupplier.standardBatchSize;

	theMainChainOfResources = _theMainChainOfResources;
	distro = _distro;
	allProd = vp;
	allNodes = new Node[vp.length];
	for(int j=0; j<vp.length; j++) {
	    Production p = vp[j];
	    allNodes[j] = new Node(p, j);
	}
	root = new Node(rawMatSupplier, -1);

	// Pro forma analysis, w/o capacity constraints
	doAnalyze(1.0, false, false);

	if (!Demo.quiet) {
	    System.out.println("========== Production Graph Report ===============");
	    System.out.println(report( false));
	    System.out.println("==================================================");
	}

	//System.exit(0);
    }


    /** Extracts a few command-line options we understand, and leaves
	the rest of them to MASON.
    */
    public static void main(String[] argv) throws IOException, IllegalInputException {

	Demo.MakesDemo maker = new Demo.MakesDemo(argv);
	argv = maker.argvStripped;
	
	//doLoop(Demo.class, argv);
	//doLoop(maker, argv);
	Demo demo = (Demo)maker.newInstance(0L, argv);
	demo.initSupplyChain();

	GraphAnalysis ga = demo.getPharmaCompany().getGraphAnalysis();

	// Analysis, with capacity constraints	
	ga.doAnalyze( 3.7903250e7, true, false);

	System.out.println("========== Production Graph Report 2 =============");
	System.out.println(ga.report(true));
	System.out.println("==================================================");

	// Restore the pro forma numbers, for later use
	// ga.doAnalyze(1.0, false, false);


       
	System.exit(0);
    }
    
    
}
    
