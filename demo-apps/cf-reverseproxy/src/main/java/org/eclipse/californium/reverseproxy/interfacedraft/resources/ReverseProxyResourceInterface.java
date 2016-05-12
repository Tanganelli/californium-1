package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.ResourceAttributes;
import org.eclipse.californium.examples.ExampleReverseProxy;
import org.eclipse.californium.reverseproxy.interfacedraft.InterfaceRequest;
import org.eclipse.californium.reverseproxy.resources.ClientEndpoint;
import org.eclipse.californium.reverseproxy.resources.ReverseProxyResource;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ReverseProxyResourceInterface extends ReverseProxyResource {
    /** The factor that multiplied for the actual RTT
     * is used as the timeout for waiting replies from the end device.*/
    private static long WAIT_FACTOR = 10;

    private long notificationPeriodMin;
    private long notificationPeriodMax;
    private long rtt;
    private long lastValidRtt;
    private final Map<ClientEndpoint, InterfaceRequest> subscriberList;
    private CoapObserveRelation relation;

    public ReverseProxyResourceInterface(String name, URI uri, ResourceAttributes resourceAttributes, NetworkConfig networkConfig, ExampleReverseProxy reverseProxy) {
        super(name, uri, resourceAttributes, networkConfig, reverseProxy);
        this.rtt = 500;
        subscriberList = new HashMap<ClientEndpoint, InterfaceRequest>();
        this.addObserver(new ReverseProxyResourceObserver(this));
        notificationPeriodMin = 0;
        notificationPeriodMax = Integer.MAX_VALUE;
        relation = null;
    }

    /**
     * Forward incoming request to the end device.
     *
     * @param request the request received from the client
     * @return the response received from the end device
     */
    public Response forwardRequest(Request incomingRequest) {
        LOGGER.info("ProxyCoAP2CoAP forwards "+ incomingRequest);

        incomingRequest.getOptions().clearUriPath();

        // create a new request to forward to the requested coap server
        Request outgoingRequest = null;

        // create the new request from the original
        outgoingRequest = getRequest(incomingRequest);


        // execute the request
        LOGGER.finer("Sending coap request.");

        LOGGER.info("ProxyCoapClient received CoAP request and sends a copy to CoAP target");
        outgoingRequest.send(this.getEndpoints().get(0));

        // accept the request sending a separate response to avoid the
        // timeout in the requesting client
        Response receivedResponse;
        try {
            if(rtt == -1){
                receivedResponse = outgoingRequest.waitForResponse(5000);
            } else
            {
                receivedResponse = outgoingRequest.waitForResponse(rtt * WAIT_FACTOR);
            }
            // receive the response


            if (receivedResponse != null) {
                LOGGER.finer("Coap response received.");
                // get RTO from the response
                this.rtt = receivedResponse.getRemoteEndpoint().getCurrentRTO();
                // create the real response for the original request
                return getResponse(receivedResponse);
            } else {
                LOGGER.warning("No response received.");
                return new Response(CoAP.ResponseCode.GATEWAY_TIMEOUT);
            }
        } catch (InterruptedException e) {
            LOGGER.warning("Receiving of response interrupted: " + e.getMessage());
            return new Response(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles the PUT request in the given CoAPExchange.
     * If request contains pmin and pmax parse values and:
     *  - Successful reply (CHANGED) if values are conforming to CoRE Interfaces
     *  - BAD_REQUEST if values are less than 0.
     * If it is a normal PUT request, forwards it to the end device.
     *
     * @param exchange the CoapExchange for the simple API
     */
    @Override
    public void handlePUT(CoapExchange exchange) {
        LOGGER.log(Level.FINER, "handlePUT(" + exchange + ")");
        Request request = exchange.advanced().getRequest();
        List<String> queries = request.getOptions().getUriQuery();
        if(!queries.isEmpty()){
            int ret = handlePUTCoRE(exchange);
            // pmin or pmax
            if(ret == 0){
                exchange.respond(CoAP.ResponseCode.CHANGED);
            } else {
                super.handlePUT(exchange);
            }
        }
        else{
            super.handlePUT(exchange);
        }
    }

    /**
     * Checks if pmin and pmax are contained as UriQuery.
     * If yes, store them and replies with CHANGED.
     *
     * @param exchange the exchange that own the incoming request
     * @return the ResponseCode to used in the reply to the client
     */
    private int handlePUTCoRE(CoapExchange exchange) {
        LOGGER.log(Level.INFO, "handlePUTCoRE(" + exchange + ")");
        Request request = exchange.advanced().getCurrentRequest();
        List<String> queries = request.getOptions().getUriQuery();
        ClientEndpoint clientEndpoint = new ClientEndpoint(request.getSource(), request.getSourcePort());
        InterfaceRequest interface_request = getSubscriberCopy(clientEndpoint);
        if(interface_request  == null)
            interface_request = new InterfaceRequest();
        double pmin = -1;
        double pmax = -1;
        for(String composedquery : queries){
            //handle queries values
            String[] tmp = composedquery.split("=");
            if(tmp.length != 2) // not valid Pmin or Pmax
                return 1;
            String query = tmp[0];
            String value = tmp[1];
            if(query.equals(CoAP.MINIMUM_PERIOD)){
                double seconds;
                try{
                    seconds = Double.parseDouble(value);
                    if(seconds <= 0) throw new NumberFormatException();
                } catch(NumberFormatException e){
                    return 1;
                }
                pmin = seconds * 1000; //convert to milliseconds
            } else if(query.equals(CoAP.MAXIMUM_PERIOD)){
                double seconds = -1;
                try{
                    seconds =  Double.parseDouble(value);
                    if(seconds <= 0) throw new NumberFormatException();
                } catch(NumberFormatException e){
                    return 1;
                }
                pmax = seconds * 1000; //convert to milliseconds
            }
        }
        if(pmin > pmax)
            return 1;
        // Minimum and Maximum period has been set
        if(pmin != -1 && pmax != -1){
            interface_request.setAllowed(false);
            interface_request.setPmax(pmax);
            interface_request.setPmin(pmin);
            addSubscriber(clientEndpoint, interface_request);
        }
        return 0;
    }

    private synchronized InterfaceRequest getSubscriberCopy(ClientEndpoint clientEndpoint) {
        LOGGER.log(Level.INFO, "getSubscriberCopy(" + clientEndpoint + ")");
        return this.subscriberList.get(clientEndpoint);
    }

    private synchronized void addSubscriber(ClientEndpoint clientEndpoint, InterfaceRequest pr) {
        LOGGER.log(Level.FINER, "addSubscriber(" + clientEndpoint+ ", "+ pr +")");
        this.subscriberList.put(clientEndpoint, pr);
    }
}
