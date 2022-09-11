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
	
/** This class exists so that we can figure in advance what would happen with a 1000 units of RM dumped into the system. Assuming that everything works as designed (with the split ratios, fault rates, etc, already in the system), how much of this RM will eventually arrive to the DC in the form of this finished product? On the way there, how much will pass through each production node? 

We use these numbers to compute the amount of RM that needs to be ordered to produced a desired amount of the finished product. Additionally, we also use these numbers to provide intelligent work orders to production nodes that need orders (because they have safety stocks, and cannot be controlled just by the RM supply).
 */

public class GraphAnalysis {

    private final Distributor distro;
    
    static private DecimalFormat df = new DecimalFormat("0.00##");
	
    
    class Node {
	Production element;
	/** If true, this node feeds to DC */
	boolean terminal = false;
	double terminalAmt = 0;
	/** Output/input ratio of this node. Typically, it's (1- E(faultRate)).
	 */
	double alpha;
	HashMap<Integer,RData> outputs = new HashMap<>();
	/** How much of a 1.000 of the RM that enters the root will
	    reach the input of that node */
	double inputAmt = 0;

	/** @param x Either a fully-configured Production element, or
	    rawMaterialSupplier. It should have all its outputs etc
	    already properly set
	 */
	Node(//Production x
	     HasQA _x) {
	    Production x = (_x instanceof Production)? (Production)_x: null;
	    if (x!=null) {
		element = x;
		alpha = x.computeAlpha();
	    } else {
		element = null;
		alpha = 1;
	    }
	    ArrayList<Receiver> rr = _x.getQaDelay().getReceivers();
	    if (rr.size()!=1) throw new IllegalArgumentException("QA is expected to have exactly 1 receiver");
	    Receiver _r = rr.get(0);
	    if (_r == distro) terminal = true;
	    else if (_r instanceof Production) {
		Production r = (Production)_r;
		outputs.put(prod2no(r), new RData(1.0));
	    } else if (_r instanceof Production.InputStore) {
		Production r = ((Production.InputStore)_r).whose();
		outputs.put(prod2no(r), new RData(1.0));
	    } else if (_r instanceof Splitter) {
		Splitter s  = (Splitter)_r;
		double sf = s.computeSumF(s.data.keySet());
		if (sf == 0) throw  new IllegalArgumentException("All fractions are 0");
		for(Receiver _z: s.data.keySet()) {
		    if (_z instanceof Production.InputStore) {
			Production z = ((Production.InputStore)_z).whose();
			RData q = s.data.get(_z);
			outputs.put(prod2no(z), new RData(q.fraction/sf));
		    } else throw new IllegalArgumentException("Splitter ought to feed to Production nodes only");
		}
				 
	    } else throw  new IllegalArgumentException("Unexpected type for receiver: " + _r.getName());
	}

	String report() {	    
	    String s = (element==null)? "Root": element.getName();
	    s += ": input=" + df.format(inputAmt) +
		", alpha=" +df.format(alpha)+". Send: ";
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


    public double terminalAmt = 0;

    private void analyze(Node root, double input) {
	root.inputAmt += input;
	if (root.terminal) {
	    double sent = input * root.alpha;
	    root.terminalAmt += sent;
	    terminalAmt += sent;
	    return;
	}
	for(int j: root.outputs.keySet()) {
	    RData d = root.outputs.get(j);
	    Node y = allNodes.get(j);
	    double sent = input * root.alpha * d.fraction;
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
    
