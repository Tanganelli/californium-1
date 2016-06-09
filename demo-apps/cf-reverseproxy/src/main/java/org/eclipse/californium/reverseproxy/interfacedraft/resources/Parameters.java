package org.eclipse.californium.reverseproxy.interfacedraft.resources;

public class Parameters {

	private double pmin;
    private double pmax;
    private boolean allowed;
    
    public Parameters(double pmin, double pmax, boolean allowed){
    	setPmin(pmin);
    	setPmax(pmax);
    	setAllowed(allowed);
    }
    
    public double getPmin() {
        return pmin;
    }

    public void setPmin(double pmin) {
        this.pmin = pmin;
    }

    public double getPmax() {
        return pmax;
    }

    public void setPmax(double pmax) {
        this.pmax = pmax;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
}
