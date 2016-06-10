package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import java.util.Date;
import java.util.logging.Logger;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObserveRelationFilter;
import org.eclipse.californium.reverseproxy.resources.ClientEndpoint;

public class InterfaceObserveRelationFilter implements ObserveRelationFilter {

	private ReverseProxyResourceInterface interface_resource;
	protected final static Logger LOGGER = Logger.getLogger(InterfaceObserveRelationFilter.class.getCanonicalName());
	private final static int GAP_BOUND = 1000;
	public InterfaceObserveRelationFilter(ReverseProxyResourceInterface interface_resource){
		this.interface_resource = interface_resource;
	}
	
	@Override
	public boolean accept(ObserveRelation relation) {
		
		InterfaceObserveRelation rel = (InterfaceObserveRelation) relation;
		LOGGER.info("AcceptFilter (" + ((long)rel.getPmin()) + ", " + ((long)rel.getPmax()) + ")");
		long nextInterval;
        long deadline;
        Date now = new Date();
        long timestamp = now.getTime();
        double delay = interface_resource.getNotificationPeriod();
		if(rel.isAllowed()){
    		//Request request = rel.getExchange().getRequest();
    		//ClientEndpoint cl = new ClientEndpoint(request.getSource(), request.getSourcePort());
    		//long clientRTT = interface_resource.getReverseProxy().getClientRTT(cl.getAddress(), cl.getPort());
    		long last_timestamp = rel.getLastTimestamp();
    		long threshold = (((long)rel.getPmax()) - ((long)rel.getPmin())) * 1 / 100;
    		nextInterval = timestamp + ((long)delay);
    		if(last_timestamp == 0){
                deadline = timestamp;
            }
            else{
            	
                deadline = last_timestamp + ((long)rel.getPmax()) + threshold;
            }
    		synchronized(this){
    			if(nextInterval > deadline) System.out.print("[Send Notification ");
	    		else System.out.print("[ABORT Notification ");
	    		System.out.println("Client (" + ((long)rel.getPmin()) + ", " + ((long)rel.getPmax()) + ")");
	    		System.out.println("Now: " + timestamp);
	    		System.out.println("Delay: " + (long)delay);
	    		System.out.println("Last Timestamp: " + last_timestamp);
	    		System.out.println("Next Interval: " + nextInterval);
	    		System.out.println("Threshold: " + threshold);
	    		System.out.println("Deadline: " + deadline + "]");
    		}
    		if(nextInterval > deadline){
    			rel.setLastTimestamp(timestamp);
    			return true;
    		}
		}
		return false;
	}

}
