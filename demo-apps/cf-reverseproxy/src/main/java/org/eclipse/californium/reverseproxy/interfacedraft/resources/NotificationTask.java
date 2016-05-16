package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.reverseproxy.interfacedraft.InterfaceRequest;
import org.eclipse.californium.reverseproxy.resources.ClientEndpoint;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread class to send periodic notifications to clients.
 *
 */
public class NotificationTask implements Runnable{

    private boolean to_change = true;
    private ReverseProxyResourceInterface interface_resource;
    /** The logger. */
    protected final static Logger LOGGER = Logger.getLogger(NotificationTask.class.getCanonicalName());

    public NotificationTask(ReverseProxyResourceInterface interface_resource){
        this.interface_resource = interface_resource;
    }
    @Override
    public void run() {
        while(interface_resource.observeEnabled.get()){
            LOGGER.log(Level.INFO, "NotificationTask Run");
            double delay = interface_resource.getNotificationPeriod();
            if(interface_resource.getRelation() == null || interface_resource.getRelation().getCurrent() != null){
                Map<ClientEndpoint, InterfaceRequest> tmp = interface_resource.getSubscriberListCopy();
                for(Map.Entry<ClientEndpoint, InterfaceRequest> entry : tmp.entrySet()){
                    InterfaceRequest pr = entry.getValue();
                    ClientEndpoint cl = entry.getKey();
                    LOGGER.info("Entry - " + pr.toString() + ":" + pr.isAllowed());
                    if(pr.isAllowed()){
                        Date now = new Date();
                        long timestamp = now.getTime();
                        long clientRTT = interface_resource.getReverseProxy().getClientRTT(cl.getAddress(), cl.getPort());
                        long nextInterval = 0;
                        long deadline = 0;
                        if(pr.getTimestampLastNotificationSent() == -1){
                            nextInterval = (timestamp + ((long)pr.getPmin()));
                            deadline = timestamp + ((long)pr.getPmax() - clientRTT);
                        }
                        else{
                            nextInterval = (pr.getTimestampLastNotificationSent() + ((long)pr.getPmin()));
                            deadline = pr.getTimestampLastNotificationSent() + ((long)pr.getPmax() - clientRTT);
                        }
							/*System.out.println("RTT " + rtt);
							System.out.println("timestamp " + timestamp);
							System.out.println("next Interval " + nextInterval);
							System.out.println("client RTT " + clientRTT);
							System.out.println("deadline without rtt " + deadlinewithout );
							System.out.println("deadline " + deadline);*/
                        if(timestamp >= nextInterval){
                            System.out.println("Time to send");
                            if(pr.getLastNotificationSent().equals(interface_resource.getRelation().getCurrent().advanced())){ //old notification
                                System.out.println("Old Notification");
                                if(delay > (deadline - timestamp) && (deadline - timestamp) >= 0)
                                    delay = (deadline - timestamp);
                                //System.out.println("Delay " + delay);
                                //if((deadline - timestamp) < 0)
                                //sendValidated(cl, pr, timestamp);

                            } else{
                                System.out.println("New notification");
                                if(to_change)
                                    sendValidated(cl, timestamp);
                                to_change = false;

                            }
                        } else { // too early
                            System.out.println("Too early");
                            double nextawake = timestamp + delay;
                            //System.out.println("next Awake " + nextawake);
                            if(nextawake >= deadline){ // check if next awake will be to late
                                if(delay > (deadline - timestamp))
                                    delay = (deadline - timestamp);
                            }
                            //System.out.println("Delay " + delay);
                        }
                    }

                }
            }
            to_change = true;
            LOGGER.info("Delay " + delay);
            try {
                long waittime = (long) Math.floor(delay);
                interface_resource.lock.lock();
                interface_resource.newNotification.await(waittime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                interface_resource.lock.unlock();
            }
        }
    }

    /**
     * Send if the last notification is still valid
     * @param cl
     *
     * @param timestamp the timestamp
     */
    private void sendValidated(ClientEndpoint cl, long timestamp) {
        LOGGER.log(Level.FINER, "sendValidated("+ cl+", "+timestamp+")");
        long timestampResponse = interface_resource.getRelation().getCurrent().advanced().getTimestamp();
        Response response = interface_resource.getRelation().getCurrent().advanced();
        long maxAge = response.getOptions().getMaxAge();

        if(timestampResponse + (maxAge * 1000) > timestamp){ //already take into account the rtt experimented by the notification
            LOGGER.info("sendValidated to be sent("+ cl+", "+timestamp+")");
				/*updateSubscriberNotification(cl, timestamp, response);
				Response responseForClients = new Response(response.getCode());
				// copy payload
				byte[] payload = response.getPayload();
				responseForClients.setPayload(payload);

				// copy the timestamp
				responseForClients.setTimestamp(timestamp);

				// copy every option
				responseForClients.setOptions(new OptionSet(
						response.getOptions()));
				responseForClients.setDestination(cl.getAddress());
				responseForClients.setDestinationPort(cl.getPort());
				responseForClients.setToken(pr.getOriginRequest().getToken());

				pr.getExchange().respond(responseForClients);*/
            interface_resource.changed();
        } else {
            LOGGER.severe("Response no more valid");
        }

    }
}