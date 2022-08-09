package  edu.rutgers.pharma3;

class Timer {
    private Double onUntil = null;
    void disable() { onUntil=null; }
    void enableUntil(double x) {
	if (onUntil==null || onUntil <  x) {
	    onUntil = x;
	}
    }
    boolean isOn(double now) {
	if (onUntil!=null && onUntil<=now) onUntil=null;
	return (onUntil !=null);	    	    
    }
}

