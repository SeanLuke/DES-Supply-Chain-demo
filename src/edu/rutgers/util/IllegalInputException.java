package edu.rutgers.util;

/** An exception of this type is thrown when we want to report to the user that 
    the data he's entered are invalid
 */
public class IllegalInputException extends Exception 
{
    public IllegalInputException(String msg) {
	super(msg);
    }


}
