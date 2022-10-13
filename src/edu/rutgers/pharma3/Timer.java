package  edu.rutgers.pharma3;

/** An auxiliary class which can be "activated" to return certain
    value until a specified date. After that date comes, the the
    Timer automatically deactivates itself. 

    This class is used as a flag that can be set at the beginning of a
    disruption of a known length, so that it will indicate, for a
    desired length of time, that the disruption is in effect.

    Optionally, the Timer may be used to return not just a boolean
    value ("is the disruption on?"), but a double value ("how strong
    the disruption is").
*/
class Timer {
    private Double onUntil = null;
    private double value;
    
    void disable() { onUntil=null; }
    /** Activates the timer, so that it will stay on until the 
	desired time.
	@param t The timer will stay "on" at least until this
	time. (It may stay on longer if it's already activated
	until a later date, or if it is reactivated later on).
    */
    void enableUntil(double t) {
	if (onUntil==null || onUntil <  t) {
	    onUntil = t;
	}
    }
    /** Activates the timer, and tells it to return a specified
	value in future checks, for as along as it's on */
    void setValueUntil(double x, double t) {
	if (onUntil==null || onUntil <  t) {
	    onUntil = t;
	}
	value = x;
    }
    boolean isOn(double now) {
	if (onUntil!=null && onUntil<=now) onUntil=null;
	return (onUntil !=null);	    	    
    }
    double getValue(double now) {
	if (!isOn(now)) value=0;
	return value;
    }

}

