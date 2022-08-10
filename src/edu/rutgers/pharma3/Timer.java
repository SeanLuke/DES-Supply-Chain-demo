package  edu.rutgers.pharma3;

class Timer {
    private Double onUntil = null;
    private double value;
    
    void disable() { onUntil=null; }
    void enableUntil(double t) {
	if (onUntil==null || onUntil <  t) {
	    onUntil = t;
	}
    }
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

