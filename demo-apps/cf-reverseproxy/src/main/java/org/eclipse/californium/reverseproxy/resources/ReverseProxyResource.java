package org.eclipse.californium.reverseproxy.resources;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.ResourceAttributes;
import org.eclipse.californium.examples.ExampleReverseProxy;
import org.eclipse.californium.reverseproxy.PeriodicRequest;
import org.eclipse.californium.reverseproxy.QoSParameters;
import org.eclipse.californium.reverseproxy.ReverseProxy;


public class ReverseProxyResource extends CoapResource {

    /** The logger. */
    protected final static Logger LOGGER = Logger.getLogger(ReverseProxyResource.class.getCanonicalName());



    private final URI uri;

    //private final Map<ClientEndpoint, PeriodicRequest> invalidSubscriberList;



    public ExampleReverseProxy getReverseProxy() {
        return reverseProxy;
    }

    private ExampleReverseProxy reverseProxy;


    public ReverseProxyResource(String name, URI uri, ResourceAttributes resourceAttributes, NetworkConfig networkConfig, ExampleReverseProxy reverseProxy) {
        super(name);
        this.uri = uri;
        for(String key : resourceAttributes.getAttributeKeySet()){
            for(String value : resourceAttributes.getAttributeValues(key))
                this.getAttributes().addAttribute(key, value);
        }
        if(! this.getAttributes().getAttributeValues(LinkFormat.OBSERVABLE).isEmpty()){
            this.setObservable(true);
            setObserveType(Type.CON);
        }
        this.reverseProxy = reverseProxy;

    }

    @Override
    public void handleRequest(final Exchange exchange) {
        LOGGER.log(Level.FINER, "handleRequest(" + exchange + ")");
        exchange.sendAccept();
        Code code = exchange.getRequest().getCode();
        switch (code) {
            case GET:	handleGET(new CoapExchange(exchange, this)); break;
            case POST:	handlePOST(new CoapExchange(exchange, this)); break;
            case PUT:	handlePUT(new CoapExchange(exchange, this)); break;
            case DELETE: handleDELETE(new CoapExchange(exchange, this)); break;
        }
    }

    /**
     * Handles the POST request in the given CoAPExchange. Forward request to end device.
     *
     * @param exchange the CoapExchange for the simple API
     */
    public void handlePOST(CoapExchange exchange) {
        Response response = forwardRequest(exchange.advanced().getRequest());
        exchange.respond(response);
    }

    /**
     * Handles the DELETE request in the given CoAPExchange. Forward request to end device.
     *
     * @param exchange the CoapExchange for the simple API
     */
    public void handleDELETE(CoapExchange exchange) {
        Response response = forwardRequest(exchange.advanced().getRequest());
        exchange.respond(response);
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
    public void handlePUT(CoapExchange exchange) {
        Response response = forwardRequest(exchange.advanced().getRequest());
        exchange.respond(response);
    }

    /**
     * Handles the GET request in the given CoAPExchange. Checks if it is an observing request
     * for which pmin and pmax have been already set.
     * If it is an observing request:
     *  - Successfully reply if observing can be established with the already set pmin and pmax.
     *  - Reject if pmin or pmax has not been set.
     * If it is a normal GET forwards it to the end device.
     *
     * @param exchange the CoapExchange for the simple API
     */
    public void handleGET(CoapExchange exchange) {
        Response response = forwardRequest(exchange.advanced().getRequest());
        exchange.respond(response);
    }

    /**
     * Forward incoming request to the end device.
     *
     * @param incomingRequest the request received from the client
     * @return the response received from the end device
     */
    public Response forwardRequest(Request incomingRequest) {
        LOGGER.info("ProxyCoAP2CoAP forwards "+ incomingRequest);

        incomingRequest.getOptions().clearUriPath();

        // create a new request to forward to the requested coap server
        Request outgoingRequest;

        // create the new request from the original
        outgoingRequest = getRequest(incomingRequest);


        LOGGER.info("ProxyCoapClient received CoAP request and sends a copy to CoAP target");
        outgoingRequest.send(this.getEndpoints().get(0));

        // accept the request sending a separate response to avoid the
        // timeout in the requesting client
        Response receivedResponse;
        try {

            receivedResponse = outgoingRequest.waitForResponse(5000);

            // receive the response
            if (receivedResponse != null) {
                LOGGER.finer("Coap response received.");
                // create the real response for the original request

                return getResponse(receivedResponse);
            } else {
                LOGGER.warning("No response received.");
                return new Response(ResponseCode.GATEWAY_TIMEOUT);
            }
        } catch (InterruptedException e) {
            LOGGER.warning("Receiving of response interrupted: " + e.getMessage());
            return new Response(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void setTimestamp(long timestamp) {
        LOGGER.log(Level.FINER, "setTimestamp(" + timestamp + ")");
        relation.getCurrent().advanced().setTimestamp(timestamp);
        // Update also Max Age to consider Server RTT
        LOGGER.info("MAX-AGE " + relation.getCurrent().advanced().getOptions().getMaxAge().toString());
        LOGGER.info("RTT " + rtt);
        relation.getCurrent().advanced().getOptions().setMaxAge(relation.getCurrent().advanced().getOptions().getMaxAge() - (rtt / 1000));
    }

    public long getRtt() {
        return rtt;
    }

    public void setRtt(long rtt) {
        this.rtt = rtt;
    }

    /**
     * Invoked by the Resource Observer handler when a client cancel an observe subscription.
     *
     * @param clientEndpoint the Periodic Observing request that must be deleted
     * @param cancelledRelation
     */
    public void deleteSubscriptionsFromClients(ClientEndpoint clientEndpoint) {
        LOGGER.log(Level.INFO, "deleteSubscriptionsFromClients(" + clientEndpoint + ")");
        if(clientEndpoint != null){
            removeSubscriber(clientEndpoint);

            if(getSubscriberListCopy().isEmpty()){
                LOGGER.log(Level.INFO, "SubscriberList Empty");
                observeEnabled.set(false);
                lock.lock();
                newNotification.signalAll();
                lock.unlock();
                relation.proactiveCancel();
            } else{
                scheduleFeasibles();
            }
        }
    }



    private synchronized PeriodicRequest getSubscriber(ClientEndpoint clientEndpoint) {
        LOGGER.log(Level.FINER, "getSubscriber(" + clientEndpoint + ")");
        if(this.subscriberList.containsKey(clientEndpoint))
            return this.subscriberList.get(clientEndpoint);
        return null;
    }

//	private synchronized void addInvalidSubscriber(ClientEndpoint client,
//			PeriodicRequest pr) {
//		LOGGER.log(Level.INFO, "addInvalidSubscriber(" + client+ ", "+ pr +")");
//		this.invalidSubscriberList.put(client, pr);
//	}
//
//	private synchronized void removeInvalidSubscriber(ClientEndpoint clientEndpoint) {
//		LOGGER.log(Level.INFO, "removeInvalidSubscriber(" + clientEndpoint + ")");
//		this.invalidSubscriberList.remove(clientEndpoint);
//	}
//
//	public synchronized Map<ClientEndpoint, PeriodicRequest> getInvalidSubscriberList() {
//		LOGGER.log(Level.INFO, "getInvalidSubscriberList()");
//		Map<ClientEndpoint, PeriodicRequest> tmp = new HashMap<ClientEndpoint, PeriodicRequest>();
//		for(Entry<ClientEndpoint, PeriodicRequest> entry : this.invalidSubscriberList.entrySet()){
//			ClientEndpoint cl = new ClientEndpoint(entry.getKey().getAddress(), entry.getKey().getPort());
//			PeriodicRequest pr = new PeriodicRequest();
//			pr.setAllowed(entry.getValue().isAllowed());
//			pr.setCommittedPeriod(entry.getValue().getCommittedPeriod());
//			pr.setExchange(entry.getValue().getExchange());
//			pr.setLastNotificationSent(entry.getValue().getLastNotificationSent());
//			pr.setOriginRequest(entry.getValue().getOriginRequest());
//			pr.setPmax(entry.getValue().getPmax());
//			pr.setPmin(entry.getValue().getPmin());
//			pr.setTimestampLastNotificationSent(entry.getValue().getTimestampLastNotificationSent());
//			pr.setResponseCode(entry.getValue().getResponseCode());
//			tmp.put(cl, pr);
//		}
//		return tmp;
//	}
//
//	private synchronized boolean invalidSubscriverEmpty() {
//		return this.invalidSubscriberList.isEmpty();
//	}

    /**
     * Translate request coming from the end device into a response for the client.
     *
     * @param incomingResponse response from end device
     * @return response for the client
     */
    protected Response getResponse(final Response incomingResponse) {
        if (incomingResponse == null) {
            throw new IllegalArgumentException("incomingResponse == null");
        }

        // get the status
        ResponseCode status = incomingResponse.getCode();

        // create the response
        Response outgoingResponse = new Response(status);

        // copy payload
        byte[] payload = incomingResponse.getPayload();
        outgoingResponse.setPayload(payload);

        // copy the timestamp
        long timestamp = incomingResponse.getTimestamp();
        outgoingResponse.setTimestamp(timestamp);

        // copy every option
        outgoingResponse.setOptions(new OptionSet(
                incomingResponse.getOptions()));

        LOGGER.finer("Incoming response translated correctly");
        return outgoingResponse;
    }

    /**
     * Translates request from the client to the end device.
     *
     * @param incomingRequest the request from the client
     * @return the request for the end device
     */
    protected Request getRequest(final Request incomingRequest) {
        // check parameters
        if (incomingRequest == null) {
            throw new IllegalArgumentException("incomingRequest == null");
        }

        // get the code
        Code code = incomingRequest.getCode();

        // get message type
        Type type = incomingRequest.getType();

        // create the request
        Request outgoingRequest = new Request(code);
        outgoingRequest.setConfirmable(type == Type.CON);

        // copy payload
        byte[] payload = incomingRequest.getPayload();
        outgoingRequest.setPayload(payload);

        // get the uri address from the proxy-uri option
        URI serverUri = this.uri;

        // copy every option from the original message
        // do not copy the proxy-uri option because it is not necessary in
        // the new message
        // do not copy the token option because it is a local option and
        // have to be assigned by the proper layer
        // do not copy the block* option because it is a local option and
        // have to be assigned by the proper layer
        // do not copy the uri-* options because they are already filled in
        // the new message
        OptionSet options = new OptionSet(incomingRequest.getOptions());
        options.removeProxyUri();
        options.removeBlock1();
        options.removeBlock2();
        options.clearUriPath();
        options.clearUriQuery();
        outgoingRequest.setOptions(options);

        // set the proxy-uri as the outgoing uri
        if (serverUri != null) {
            outgoingRequest.setURI(serverUri);
        }

        LOGGER.finer("Incoming request translated correctly");
        return outgoingRequest;
    }
}
