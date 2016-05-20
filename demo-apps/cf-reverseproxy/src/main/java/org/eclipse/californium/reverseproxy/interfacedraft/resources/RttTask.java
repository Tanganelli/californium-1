package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import java.util.logging.Level;
import java.util.logging.Logger;


public class RttTask implements Runnable {


    private static final long PERIOD_RTT = 10000; // 10 sec
    private static final long THRESHOLD = 500; // 500 ms as threshold

    //private static final int RENEW_COUNTER = 10;
    //private int count = 0;

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
            updateRTT(interface_resource.evaluateRtt());
            try {
                Thread.sleep((long) Math.max(PERIOD_RTT, interface_resource.getNotificationPeriod()));
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * Update the RTT of this resource.
     * If the new RTT is worse (higher) than the one adopted by the scheduler previously,
     * checks if the scheduling schema is still feasible.
     *
     * @param currentRTO the new RTT
     */
    public void updateRTT(long currentRTO) {
        LOGGER.log(Level.FINER, "updateRTO(" + currentRTO + ")");
        LOGGER.info("Last Valid RTT= " + String.valueOf(interface_resource.getLastValidRtt()) + " - currentRTO= " + String.valueOf(currentRTO));
        interface_resource.setRtt(currentRTO);
        if((currentRTO - THRESHOLD) > interface_resource.getLastValidRtt()){ //worse RTT
            interface_resource.scheduleFeasibles();
        } /*else if(!invalidSubscriverEmpty()){ // better RTT and pending requests
			Map<ClientEndpoint, PeriodicRequest> tmpInvalid = getInvalidSubscriberList();
			Map<ClientEndpoint, PeriodicRequest> tmpSubscriber = getSubscriberList();
			boolean changed = false;
			for(Entry<ClientEndpoint, PeriodicRequest> entry : tmpInvalid.entrySet()){
				if(!tmpSubscriber.containsKey(entry.getKey())){ //not in subscriber
					if(entry.getValue().getPmax() < rtt){
						addSubscriber(entry.getKey(), entry.getValue());
						removeInvalidSubscriber(entry.getKey());
						changed = true;
					}
				} else {
					removeInvalidSubscriber(entry.getKey());
				}
			}
			if(changed) scheduleFeasibles();
		}*/
    }

    /*private long renewRegistration() {
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
    }*/


}

