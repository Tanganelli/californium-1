package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveRelationImpl;
import org.eclipse.californium.core.observe.ObservingEndpoint;
import org.eclipse.californium.core.server.resources.Resource;


public class InterfaceObserveRelation extends ObserveRelationImpl {

	
    private double pmin;
    private double pmax;
    private boolean allowed;
    private long lastTimestamp;
    /**
     * Constructs a new observe relation.
     *
     * @param endpoint the observing endpoint
     * @param resource the observed resource
     * @param exchange the exchange that tries to establish the observe relation
     */
    public InterfaceObserveRelation(ObservingEndpoint endpoint, Resource resource, Exchange exchange, 
    		double pmin, double pmax, boolean allowed) {
        
    	super(endpoint, resource, exchange);
		
        this.pmin = pmin;
        this.pmax = pmax;
        this.allowed = allowed;
        this.lastTimestamp = 0;
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

	public long getLastTimestamp() {
		return lastTimestamp;
	}

	public void setLastTimestamp(long lastTimestamp) {
		this.lastTimestamp = lastTimestamp;
	}

}
