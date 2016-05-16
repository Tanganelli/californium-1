package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;

import java.util.logging.Logger;

/**
 * Created by giacomo on 13/05/16.
 */

public class RttTask implements Runnable {

    private static final int RENEW_COUNTER = 10;
    private int count = 0;

    private ReverseProxyResourceInterface interface_resource;
    /** The logger. */
    protected final static Logger LOGGER = Logger.getLogger(RttTask.class.getCanonicalName());
    public RttTask(ReverseProxyResourceInterface interface_resource){
        this.interface_resource = interface_resource;
    }

    @Override
    public void run() {
        while(interface_resource.observeEnabled.get()){
            LOGGER.info("RttTask");
	    		/*if(count < RENEW_COUNTER){
	    			count++;
	    			updateRTT(evaluateRtt());
	    		} else {
	    			count = 0;
	    			updateRTT(renewRegistration());
	    		}
	    		*/
            updateRTT(evaluateRtt());
            try {
                Thread.sleep(Math.max(PERIOD_RTT, notificationPeriodMin));
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private long renewRegistration() {
        Request refresh = Request.newGet();
        refresh.setOptions(relation.getRequest().getOptions());
        // make sure Observe is set and zero
        refresh.setObserve();
        // use same Token
        refresh.setToken(relation.getRequest().getToken());
        refresh.setDestination(relation.getRequest().getDestination());
        refresh.setDestinationPort(relation.getRequest().getDestinationPort());
        refresh.send(reverseProxy.getUnicastEndpoint());
        LOGGER.info("Re-registering for " + relation.getRequest());
        Response response;
        long timeout = WAIT_FACTOR;
        try {
            while(timeout < 5*WAIT_FACTOR){
                if(rtt == -1){
                    response = refresh.waitForResponse(5000 * timeout);
                } else
                {
                    response = refresh.waitForResponse(rtt * timeout);
                }
                // receive the response

                if (response != null) {
                    LOGGER.info("Coap response received. - " + response);

                    // get RTO from the response

                    //TODO uncomment
                    return response.getRemoteEndpoint().getCurrentRTO();
                } else {
                    LOGGER.warning("No response received.");
                    timeout += WAIT_FACTOR;
                }
            }
            if(timeout == 5*WAIT_FACTOR){
                LOGGER.warning("Observig cannot be set on remote endpoint.");
            }
        } catch (InterruptedException e) {
            LOGGER.warning("Receiving of response interrupted: " + e.getMessage());
        }
        return 0;
    }

}

