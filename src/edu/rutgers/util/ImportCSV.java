/*
Copyright 2018, Rutgers University, New Brunswick and Piscataway, NJ.

All Rights Reserved

Permission to use, copy, and modify this software and its
documentation for any purpose other than its incorporation into a
commercial product is hereby granted without fee, provided that the
above copyright notice appears in all copies and that both that
copyright notice and this permission notice appear in supporting
documentation, and that the names of Rutgers University, DIMACS, and
the authors not be used in advertising or publicity pertaining to
distribution of the software without specific, written prior
permission.

RUTGERS UNIVERSITY, DIMACS, AND THE AUTHORS DISCLAIM ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR ANY PARTICULAR PURPOSE. IN NO EVENT
SHALL RUTGERS UNIVERSITY, DIMACS, OR THE AUTHORS BE LIABLE FOR ANY
SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER
RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF
CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
*/

/* ©2019 Rutgers, The State University of New Jersey */
package edu.rutgers.util;

import java.io.*;
import java.util.*;

/** Methods responsible for parsing CSV files.

<p>
See ... for the file format.

    @author Vladimir Menkov

 */
public class ImportCSV {

   /** Save the context and reset the buffer */
    private static String saveB(StringBuffer b) {
	if (b.length()==0) return "";
	else {
	    String x = b.toString().trim();
	    b.setLength(0);
	    return x;
	}
    }
    

    public static String[] splitCSVTrim(String s) throws IllegalInputException {
	String[] csv=splitCSV(s);
	String[] w = new String[csv.length];
	for(int i=0; i<csv.length; i++) w[i] = csv[i].trim();
	return w;
    }

    /** Converts a single string (such as a line of CSV file) into an
	array of strings. Splits a string by commas, with a primitive
	attempt to take care of nested quotes.

	This method interprets "" as an escaped ". This is appropriate,
	for example, for CSV files saved from Google Spreadsheets.
 */
    public static String[] splitCSV(String s) throws IllegalInputException {
	final char q = '"';
	Vector<String> v = new 	Vector<String>();
	boolean inQ=false;
	StringBuffer b = new StringBuffer();
	for(int j=0; j<s.length(); j++ ) {
	    char c = s.charAt(j);
	    if (c == q) {
		if (b.length()==0 && !inQ) {
		    // token starts with a quote
		    inQ = true;
		    continue;
		} else if (inQ && j+1 < s.length() && s.charAt(j+1)==q) {
		    j++; // interpret "" as an escaped  " 
		} else if (inQ && (j+1 == s.length()  || s.charAt(j+1)==',')) {
		    // quoted token ends
		    inQ = false;
		    continue;
		} else {
		    throw new IllegalInputException("Don't know how to interpret double quote in pos " + j +", line: " + s);
		}
	    } else if (c == ',' && !inQ) {
		v.add(saveB(b)); 
		continue;
	    } else if (c == '\r' || c== '\n' ) {
		c = ' ';
	    }
	    b.append(c);
	}
	if (inQ)  throw new IllegalInputException("Missing closing double quote in the line: " + s);
	v.add(saveB(b)); 
	return v.toArray(new String[0]);
    }

    /** Parses a data file in CSV format, interpreting each non-blank (and non-comment) line of the file as the description of one expert's Prediction. 

	@param K the number of observation periods. The value should be positive if you know the number of observation periods in advance. If 0 or a negative number is given, the method will determine the number of observation periods from the first line of the input file.

	@param reader A reader associated with an input file or some other data source. Each line of the file should be in the following CSV format:
<pre>
"Expert Name", apprehensionRate, attemptRate1, attemptRate2, attemptRate3, attempLaterRate, neverAttemptRate
</pre>
Empty lines and lines starting with a '#' will be ignored.


<pre>
Last Name of Researcher,CFR DQ Crime Category,CFR Subsection,State,Citation to Applicable Law,Description of Statute,"Yellow Light?  (Needs further clarification -- enter ""Y"" if so)",Notes
 / Comments (include for any Yellow Light designation and others as necessary),Attempt / Conspiracy Included?
,,,,,,,,
,Espionage,(a)(1),NY,N/A,,,,Attempt
,,,,,,,,
,Sedition,(a)(2),NY,N/A,,,,Attempt
,,,,,,,,
,Treason,(a)(3),NY,N/A,,,,Attempt
,,,,,,,,
,Terrorism,(a)(4),NY,490.10,Solicit / Support Terrorism 2,,,Attempt
,,,,490.15,Solicit / Support Terrorism 1,,,
...
</pre>


     */
    /*
    @SuppressWarnings("unchecked")
    public static Vector<Prediction> processFile(int K, LineNumberReader reader) throws IOException, IllegalInputException {
	int errorCnt = 0;

	boolean kSet = (K>0);

	// pre-read the data
	Vector<Prediction> v = new Vector<Prediction>();
	String s=null;
	int n=0;
	errorCnt=0;
	while((s= reader.readLine())!=null) {
	    s = s.trim();
	    if (s.equals("")) continue; // ignore blank lines (such as invisible lines of "\r")
	    if (s.startsWith("#")) continue; // ignore comment lines
	    //System.out.println("Line "+(n+1)": " + s);
	    try {
		String[] a = splitCSV(s);
		if (kSet) {
		    if (K != a.length -4) throw new IllegalInputException("Value count mismatch: found " + a.length + " values in line "+ reader.getLineNumber()+"; expected " + K + "+4");
		} else { // set K based on the first data line
		    K = a.length -4;
		    kSet = true;
		}

		Prediction p = new Prediction(K, a);
		v.add(p);

	    } catch(Exception ex) {
		errorCnt++;
		String msg = "Error while processing input line " +
		    reader.getLineNumber() +  ":\n" +ex.getMessage();
		System.out.println(msg);
		throw new IllegalInputException(msg);
	    }
	}

	return v;
    }
    */

    /** Process a string for writing into a CSV file. Escapes any double quotes the string contains, and surrounds it with double quotes if needed. We also replace multiple spaces with single spaces.*/
    public static String escape(String s) {
	if (s==null) return "";
	s = s.trim().replaceAll("\\s+", " ");

	final String q = "\"";

	s = s.replaceAll(q, "'").replaceAll(",", ";");

	boolean needQ=(s.indexOf(",")>=0);

	if (s.indexOf("\"")>=0) {
	    s = s.replaceAll(q, q+q);
		needQ = true;
	}
	if (needQ) s = q+s+q;
	return s;
    }

    /** Processes all Strings for wrting into a CSV file */
    public static String escape(String ss[]) {
	Vector<String> v= new Vector<>();
	for(String s: ss) v.add( escape(s));
	return Util.join(",", v);
    }

}
