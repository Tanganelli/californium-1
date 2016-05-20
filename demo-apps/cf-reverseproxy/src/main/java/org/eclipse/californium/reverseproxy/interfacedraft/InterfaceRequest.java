package org.eclipse.californium.reverseproxy.interfacedraft;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 * Created by jacko on 12/05/16.

 * Represents an observing relationship between the client and the proxy.
 */
public class InterfaceRequest{
    private double pmin;
    private double pmax;
    private boolean allowed;
    private long lastTimestampNotificationSent;

    private CoapExchange exchange;
    private Request originRequest;
    private Response lastNotificationSent;

    public InterfaceRequest() {
        lastTimestampNotificationSent = -1;
    }

    public CoapExchange getExchange() {
        return exchange;
    }
    public void setExchange(CoapExchange exchange) {
        this.exchange = exchange;
    }
    public long getTimestampLastNotificationSent() {
        return lastTimestampNotificationSent;
    }
    public void setTimestampLastNotificationSent(long lastNotificationSent) {
        this.lastTimestampNotificationSent = lastNotificationSent;
    }

    public Response getLastNotificationSent() {
        return lastNotificationSent;
    }
    public void setLastNotificationSent(Response lastNotificationSent) {
        this.lastNotificationSent = lastNotificationSent;
    }
    public Request getOriginRequest() {
        return originRequest;
    }
    public void setOriginRequest(Request originRequest) {
        this.originRequest = originRequest;
    }

    public String toString(){
        return "("+ String.valueOf(allowed)+", "+String.valueOf(pmin)+" - "+String.valueOf(pmax)+")";
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
