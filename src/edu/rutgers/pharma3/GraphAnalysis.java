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
    
    static private DecimalFormat df = new DecimalFormat("0.00##");
	
    /** A Node typically represents a Production unit of the supply chain.
	Additionally, we also have a Node object for the root of the 
	production network, representing the RM entering the system.	
     */
    class Node {
	Production element;
	/** If true, this node feeds to DC */
	boolean terminal = false;
	double terminalAmt = 0;
	/** Output/input ratio of this node. Typically, it's computed
	    as (1-alpha-beta) / (1-beta), where alpha is the fault rate,
	    and beta is the rework rate.
	 */
	double gamma;
	/** To which other Nodes this Node feeds. The map maps the integer ID
	    of each destsination node to an RData structure containing
	    information on what fraction of the input goes to that destination
	    node.    For a terminal node,
	    this map is empty */
	HashMap<Integer,RData> outputs = new HashMap<>();
	/** How much of a 1.000 of the RM that enters the root will
	    reach the input of that node */
	double inputAmt = 0;

	/** Builds the Node from a Production element or (for the root node
	    only) from the rawMaterialSupplier. 

	    @param x Either a fully-configured Production element, or
	    rawMaterialSupplier. It should have all its outputs etc
	    already properly set.
	 */
	Node(//Production x
	     HasQA _x) {
	    Production x = (_x instanceof Production)? (Production)_x: null;
	    if (x!=null) {
		element = x;
		gamma = x.computeGamma();
	    } else {
		element = null;
		gamma = 1;
	    }
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
	    String s = (element==null)? "Root": element.getName();
	    s += ": input=" + df.format(inputAmt) +
		", gamma=" +df.format(gamma)+". Send: ";
	    if (terminal) s += "to Distributor: " + df.format(terminalAmt);
	    else {	    
		Vector<String> v = new Vector<>();
		for(int j: outputs.keySet()) {
		    RData d = outputs.get(j);
		    v.add("to " +allProd[j].getName()+ " "+ df.format(d.given)+
			  " ("+(d.fraction*100)+"%)");
		}
		s += String.join("; ", v);
	    }
	    return s;				
	}
	
    }

    /** How much of a 1.0 quantity of RM at the input of the network reaches
	the terminal (the DC), in the form of finished product. */
    public double terminalAmt = 0;

    private void analyze(Node root, double input) {
	root.inputAmt += input;
	if (root.terminal) {
	    double sent = input * root.gamma;
	    root.terminalAmt += sent;
	    terminalAmt += sent;
	    return;
	}
	for(int j: root.outputs.keySet()) {
	    RData d = root.outputs.get(j);
	    Node y = allNodes.get(j);
	    double sent = input * root.gamma * d.fraction;
	    d.given += sent;
	    analyze(y, sent);
	}
    }

    private final Production[] allProd;
    private Vector<Node> allNodes = new Vector<>();
    
    private int prod2no(Production p) {
	for(int j=0; j<allProd.length; j++) {
	    if (allProd[j]==p) return j;
	}
	throw new IllegalArgumentException("Production element not registered: " + p);
    }
					  
    private String report(Node root) {
	Vector<String> v = new Vector<>();
	v.add(root.report());
	for(int j=0; j<allProd.length; j++) {
	    v.add("["+j+"] " + allNodes.get(j).report());
	}
	v.add("Distributor receives " + df.format(terminalAmt));
	return String.join("\n", v);	
    }

    /** If the output of the entire chain is 1.0, what should 
	be the number of units started by the specified production node?
    */
    double getStartPlanFor(Production p) {
	int j = prod2no(p);
	return allNodes.get(j).inputAmt / terminalAmt;
    }

    
    GraphAnalysis(MaterialSupplier rawMatSupplier, Distributor _distro, Production[] vp) {
	distro = _distro;
	allProd = vp;
	for(Production p: vp) {
	    allNodes.add(new Node(p));
	}
	Node root = new Node(rawMatSupplier);
	analyze(root, 1.0);
    
	System.out.println("========== Production Graph Report ===============");
	System.out.println(report(root));
	System.out.println("==================================================");
	//System.exit(0);
    }
}
    
