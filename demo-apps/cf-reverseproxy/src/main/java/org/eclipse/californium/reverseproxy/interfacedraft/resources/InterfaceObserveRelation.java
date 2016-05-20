package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObservingEndpoint;
import org.eclipse.californium.core.server.resources.Resource;


public class InterfaceObserveRelation extends ObserveRelation {

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

    private double pmin;
    private double pmax;
    /**
     * Constructs a new observe relation.
     *
     * @param endpoint the observing endpoint
     * @param resource the observed resource
     * @param exchange the exchange that tries to establish the observe relation
     */
    public InterfaceObserveRelation(ObservingEndpoint endpoint, Resource resource, Exchange exchange) {
        super(endpoint, resource, exchange);
    }
}
