package  edu.rutgers.supply;

/** A Timed object can be "activated" to return a certain
    value until a specified date. After that date comes, the 
    Timed object automatically deactivates itself. 

    <p>
    This class is used as a flag that can be set at the beginning of a
    disruption of a known length, so that it will indicate, for a
    desired length of time, that the disruption is in effect.

    <p>
    Optionally, a Timed object may be used to return not just a boolean
    value ("is the disruption on?"), but a double value ("how strong
    the disruption is").

    <p>This was originally called "Timer", but renamed to "Timed" to avoid
    conflicts. One can still informally refer to a Timed object as a "timer".
*/
public class Timed {
    private Double onUntil = null;
    private double value;

    /** Deactivates the timer. */
    public void disable() { onUntil=null; }
    /** Activates the timer, so that it will stay on until the 
	desired time.
	@param t The timer will stay "on" at least until this
	time. (It may stay on longer if it's already activated
	until a later date, or if it is reactivated later on).
    */
    public void enableUntil(double t) {
	if (onUntil==null || onUntil <  t) {
	    onUntil = t;
	}
    }
    /** Activates the timer, and tells it to return a specified
	value in future checks, for as along as it's on */
    public void setValueUntil(double x, double t) {
	if (onUntil==null || onUntil <  t) {
	    onUntil = t;
	}
	value = x;
    }
    /** Is the timer on now?
	@param now The current timer. If the timer discovers that it's time
	for it to deactivate itself, it will do so.
	@return true if the timer has been activated and is still active.
	
     */
    public boolean isOn(double now) {
	if (onUntil!=null && onUntil<=now) onUntil=null;
	return (onUntil !=null);	    	    
    }
    /** Get the timer's stored value.
	@param now the current time (so that the timer will know to
	deactivate itself if needed).
	@return if the timer is on, and is storing a value, the stored
	value; 0 otherwise.
     */
    public double getValue(double now) {
	if (!isOn(now)) value=0;
	return value;
    }

}

