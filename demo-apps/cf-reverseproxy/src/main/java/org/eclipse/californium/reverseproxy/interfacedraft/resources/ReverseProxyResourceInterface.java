package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.ServerMessageDeliverer;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.ResourceAttributes;
import org.eclipse.californium.core.server.resources.ResourceObserver;
import org.eclipse.californium.examples.ExampleReverseProxy;
import org.eclipse.californium.reverseproxy.interfacedraft.Interface;
import org.eclipse.californium.reverseproxy.interfacedraft.InterfaceRequest;
import org.eclipse.californium.reverseproxy.interfacedraft.ScheduleResult;
import org.eclipse.californium.reverseproxy.interfacedraft.Scheduler;
import org.eclipse.californium.reverseproxy.interfacedraft.Task;
import org.eclipse.californium.reverseproxy.resources.ClientEndpoint;
import org.eclipse.californium.reverseproxy.resources.ReverseProxyResource;

public class ReverseProxyResourceInterface extends ReverseProxyResource {
    /** The factor that multiplied for the actual RTT
     * is used as the timeout for waiting replies from the end device.*/
    private static long WAIT_FACTOR = 10;

    private double notificationPeriod;
    private long rtt;

    private long lastValidRtt;
    private final Map<ClientEndpoint, InterfaceRequest> subscriberList;
    private final NotificationTask notificationTask;

    private RttTask rttTask;
    private final ScheduledExecutorService rttExecutor;

    private CoapObserveRelation relation;
    protected AtomicBoolean observeEnabled;
    private AtomicBoolean sendEvaluateRtt;
    private CoapClient client;
    Lock lock;
    Condition newNotification;
    private final ScheduledExecutorService notificationExecutor;
    private final Scheduler scheduler;
    
    /* The the list of CoAP observe relations. */
    private InterfaceObserveRelationContainer observeRelations;

    public ReverseProxyResourceInterface(String name, URI uri, ResourceAttributes resourceAttributes, NetworkConfig networkConfig, ExampleReverseProxy reverseProxy) {
        super(name, uri, resourceAttributes, networkConfig, reverseProxy);
        this.rtt = 500;
        subscriberList = Collections.synchronizedMap(new HashMap<ClientEndpoint, InterfaceRequest>()); 
        this.addObserver(new ReverseProxyResourceObserver(this));
        notificationPeriod = 0;
        relation = null;
        observeEnabled = new AtomicBoolean(false);
        sendEvaluateRtt = new AtomicBoolean(true);
        client = new CoapClient(uri);
        client.setEndpoint(getReverseProxy().getUnicastEndpoint());

        lock = new ReentrantLock();
        newNotification = lock.newCondition();

        notificationExecutor = Executors.newScheduledThreadPool(1);
        notificationTask = new NotificationTask(this);

        rttExecutor = Executors.newScheduledThreadPool(1);
        rttTask = new RttTask(this);

        scheduler = new Scheduler();
        
        observeRelations = new InterfaceObserveRelationContainer();
        this.setObserveRelations(observeRelations);
    }
    
    public InterfaceObserveRelationContainer getObserveRelations() {
        return observeRelations;
    }

    public double getNotificationPeriod() {
        return notificationPeriod;
    }

    public CoapObserveRelation getRelation() {
        return relation;
    }

    public void setRtt(long rtt) {
        this.rtt = rtt;
    }

    public long getLastValidRtt() {
        return lastValidRtt;
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

        // create the new request from the original
        Request outgoingRequest = getRequest(incomingRequest);


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
                // NEED CoCoA
                //this.rtt = receivedResponse.getRemoteEndpoint().getCurrentRTO();
                this.rtt = receivedResponse.getRTT();
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
    private  int handlePUTCoRE(CoapExchange exchange) {
        LOGGER.log(Level.INFO, "handlePUTCoRE(" + exchange + ")");
        Request request = exchange.advanced().getCurrentRequest();
        List<String> queries = request.getOptions().getUriQuery();
        ClientEndpoint clientEndpoint = new ClientEndpoint(request.getSource(), request.getSourcePort());
        
        InterfaceRequest interface_request = subscriberList.get(clientEndpoint);
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
            if(query.equals(Interface.MINIMUM_PERIOD)){
                double seconds;
                try{
                    seconds = Double.parseDouble(value);
                    if(seconds <= 0) throw new NumberFormatException();
                } catch(NumberFormatException e){
                    return 1;
                }
                pmin = seconds * 1000; //convert to milliseconds
            } else if(query.equals(Interface.MAXIMUM_PERIOD)){
                double seconds;
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
            subscriberList.put(clientEndpoint, interface_request);
        }
        return 0;
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
    @Override
    public void handleGET(CoapExchange exchange) {
        LOGGER.log(Level.INFO, "handleGET(" + exchange + ")");
        Request request = exchange.advanced().getRequest();
        if(request.getOptions().getObserve() != null && request.getOptions().getObserve() == 0)
        {
            //Observe Request
            //exchange.advanced().sendAccept();
        	//setRtt(evaluateRtt());
            int ret = handleGETCoRE(exchange);
            if(ret == 0){
                // create Observe request for the first client
                if(observeEnabled.compareAndSet(false, true)){
                    relation = client.observeAndWait(new ReverseProxyCoAPHandler(this));
                    Response responseForClients = getLast(request);
                    Date now = new Date();
                    long timestamp = now.getTime();
                    responseForClients.setTimestamp(timestamp);
                    
                    //updateSubscriberNotification(new ClientEndpoint(request.getSource(), request.getSourcePort()), timestamp, relation.getCurrent().advanced());
                    exchange.respond(responseForClients);
                    //LOGGER.info("Start Notification Task");
                    //notificationExecutor.submit(notificationTask);
                    
                    rttExecutor.submit(rttTask);
                }else{
                    //reply to client
                	Response responseForClients = getLast(request);
                    Date now = new Date();
                    long timestamp = now.getTime();
                    responseForClients.setTimestamp(timestamp);
                    //updateSubscriberNotification(new ClientEndpoint(request.getSource(), request.getSourcePort()), timestamp, relation.getCurrent().advanced());
                    exchange.respond(responseForClients);
                    
                    //Date now = new Date();
                    //long timestamp = now.getTime();
                    //ClientEndpoint clientEndpoint = new ClientEndpoint(request.getSource(), request.getSourcePort());
                    //InterfaceRequest interface_request = getSubscriberCopy(clientEndpoint);
                    //long elapsed = timestamp - interface_request.getTimestampLastNotificationSent();
                    //if(interface_request.getPmin() <= elapsed || interface_request.getTimestampLastNotificationSent() == -1)
                    //{
                        //Response responseForClients = getLast(request);
                        //save lastNotification for the client
                        //updateSubscriberNotification(new ClientEndpoint(request.getSource(), request.getSourcePort()), timestamp, relation.getCurrent().advanced());
                        //exchange.respond(responseForClients);
                        /*lock.lock();
                        newNotification.signalAll();
                        lock.unlock();*/
                    //}

                }
            }
            else if(ret == 2){
                Response response = getLast(request);
                response.getOptions().removeObserve();
                exchange.respond(response);
            } else {
                //ret = 1
                exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE);
            }
        }else if((request.getOptions().getObserve() != null && request.getOptions().getObserve() == 1)){

            //Cancel Observe Request
            Response responseForClients;
            if(relation == null || relation.getCurrent() == null){
                responseForClients = new Response(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            } else {
                responseForClients = getLast(request);
                responseForClients.getOptions().removeObserve();
            }
            exchange.respond(responseForClients);
        } else{
            // Normal GET
            Response response = forwardRequest(exchange.advanced().getRequest());
            exchange.respond(response);
        }
    }

//    public Map<ClientEndpoint, InterfaceRequest> getSubscriberListCopy() {
//        LOGGER.log(Level.FINER, "getSubscriberList()");
//        return this.subscriberList;
//    }

    public Parameters getParameters(ClientEndpoint clientEndpoint) {
        LOGGER.log(Level.INFO, "getParameters(" + clientEndpoint + ")");
        return new Parameters(subscriberList.get(clientEndpoint).getPmin(), subscriberList.get(clientEndpoint).getPmax(),
        		subscriberList.get(clientEndpoint).isAllowed());
    }

    /**
     * Checks if the Observing relationship can be added.
     *
     * @param exchange the exchange that generates the Observing request
     * @return the PeriodicRequest representing the Periodic Observing relationship
     */
    private int handleGETCoRE(CoapExchange exchange) {
        LOGGER.log(Level.FINER, "handleGETCoRE(" + exchange + ")");
        Request request = exchange.advanced().getCurrentRequest();
        ClientEndpoint clientEndpoint = new ClientEndpoint(request.getSource(), request.getSourcePort());
        synchronized(subscriberList){
        	InterfaceRequest interface_request = subscriberList.get(clientEndpoint);
            if(interface_request == null)
                interface_request = new InterfaceRequest();

            // Both parameters have been set
            if(interface_request.getPmin() != -1 && interface_request.getPmax() != -1){
                if(interface_request.isAllowed() || scheduleNewRequest(interface_request)){
                    interface_request.setExchange(exchange);
                    interface_request.setOriginRequest(request);
                    subscriberList.put(clientEndpoint, interface_request);
                    return 0;
                } else{
                    // Scheduling is not feasible
                	subscriberList.remove(clientEndpoint);
                    // 1 = CoAP.ResponseCode.NOT_ACCEPTABLE
                    return 1;
                }
            }
            else{
                //No parameter has been set
    			/* TODO Decide what to do in the case of an observing relationship where the client didn't set any parameter
    			 * Now we stop it and reply with an error.
    			 */
            	subscriberList.remove(clientEndpoint);
                // 2 = CoAP.ResponseCode.FORBIDDEN
                return 2;
            }
        }
        
    }

    /**
     * Verify if the new request can be accepted.
     *
     */
    private  boolean scheduleNewRequest(InterfaceRequest params) {
        LOGGER.log(Level.INFO, "scheduleNewRequest(" + params + ")");
        if(this.rtt == -1) this.rtt = evaluateRtt();
        if(params.getPmin() < this.rtt) return false;
        ScheduleResult ret = schedule();
        LOGGER.log(Level.INFO, " End scheduleNewRequest(" + params + ")");
        if(ret.isValid()){
            boolean periodChanged = updatePeriods(ret);
            if(periodChanged){
                setObservingQoS();
            }
            return true;
        }
        return false;
    }

    /**
     * Evaluates RTT of the end device by issuing a GET request.
     * @return rtt
     */
    protected long evaluateRtt() {
        LOGGER.log(Level.INFO, "evaluateRtt()");
        Request request = new Request(CoAP.Code.GET, CoAP.Type.CON);
        request.setURI(this.getURI());
        long rtt = this.rtt;
        if(sendEvaluateRtt.compareAndSet(true, false)) // only one message
        {
            request.send(this.getEndpoints().get(0));
            long timeout = WAIT_FACTOR;
            Response response;
            try {
                while(timeout < 5*WAIT_FACTOR){
                    if(rtt == -1){
                        response = request.waitForResponse(5000 * timeout);
                    } else
                    {
                        response = request.waitForResponse(rtt * timeout);
                    }

                    if (response != null) {
                        LOGGER.finer("Coap response received.");
                        // get RTO from the response
                        //Need CoCoA
                        //rtt = response.getRemoteEndpoint().getCurrentRTO() + emulatedDelay;
                        rtt = response.getRTT();
                        break;
                    } else {
                        LOGGER.warning("No response received.");
                        timeout += WAIT_FACTOR;
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.warning("Receiving of response interrupted: " + e.getMessage());
            }
            sendEvaluateRtt.set(true);
        }
        return rtt;
    }

    private Response getLast(Request request) {
        LOGGER.log(Level.INFO, "getLast(" + request + ")");
        /*lock.lock();
        try {
            while(relation == null || relation.getCurrent() == null)
                newNotification.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally{
            lock.unlock();
        }*/
        while(relation == null || relation.getCurrent() == null);
        Response notification = relation.getCurrent().advanced();

        // accept without create a new observing relationship
        Response responseForClients = new Response(notification.getCode());
        // copy payload
        byte[] payload = notification.getPayload();
        responseForClients.setPayload(payload);

        // copy every option
        responseForClients.setOptions(new OptionSet(notification.getOptions()));
        responseForClients.setDestination(request.getSource());
        responseForClients.setDestinationPort(request.getSourcePort());
        responseForClients.setToken(request.getToken());
        return responseForClients;
    }



    /**
     * Produce a scheduler schema only for feasible requests.
     */
    protected  void scheduleFeasibles() {
        LOGGER.log(Level.INFO, "scheduleFeasibles()");
        boolean end = false;
        while(!end) // delete the most demanding client
        {
            //dumpSubscribers();
            ScheduleResult ret = schedule();
            end = ret.isValid();
            if(!end){
                ClientEndpoint client = minPmaxClient();
                if(client != null){
                    LOGGER.info("Remove client:" + client.toString());
                    deleteSubscriptionFromProxy(client);
                }
                else
                    end = true;
            }
            else{
                boolean periodChanged = updatePeriods(ret);
                if(periodChanged){
                    setObservingQoS();
                }
            }

        }
    }

    /**
     * Retrieve the client with the minimum pmax.
     *
     * @return the PeriodicRequest with the minimum pmax.
     */
    private ClientEndpoint minPmaxClient() {
        LOGGER.log(Level.FINER, "minPmaxClient()");
        double minPmax = Integer.MAX_VALUE;
        ClientEndpoint ret = null;
        Set<ClientEndpoint> set = subscriberList.keySet();
        synchronized(this.subscriberList){
        	Iterator<ClientEndpoint> iterator = set.iterator(); 
            while (iterator.hasNext()){
            	ClientEndpoint ce = iterator.next();
            	InterfaceRequest request = subscriberList.get(ce);
	            if(request.getPmax() < minPmax){
	                minPmax = request.getPmax();
	                ret = ce;
            }
        }
        }
        return ret;
    }

    private  void deleteSubscriptionFromProxy(ClientEndpoint client) {
        LOGGER.log(Level.INFO, "deleteSubscriptionFromProxy(" + client + ")");
        InterfaceRequest invalid = subscriberList.get(client);
		/*invalid.setAllowed(false);
		addInvalidSubscriber(client, invalid);*/
        subscriberList.remove(client);
        Response response = getLast(invalid.getOriginRequest());
        response.getOptions().removeObserve();
        ObserveRelation rel = invalid.getExchange().advanced().getRelation();
        invalid.getExchange().advanced().setRelation(null);
        invalid.getExchange().respond(response);
        rel.cancel();
    }

    private  boolean updatePeriods(ScheduleResult ret) {
        LOGGER.log(Level.INFO, "updatePeriods(" + ret + ")");
        double period = ret.getPeriod();
        this.lastValidRtt = ret.getLastRtt();
        boolean changed = false;
        if(this.notificationPeriod != period){
            this.notificationPeriod = period;
            changed = true;
        }
        return changed;

    }

    /**
     * Create an observing request from the proxy to the end device.
     * Use the pmin and pmax computed by the scheduler.
     *
     */
    private  void setObservingQoS() {
        LOGGER.log(Level.INFO, "setObserving()");
        double period = (this.notificationPeriod) / 1000; // convert to second
        String uri = this.getURI()+"?period="+ period;
        Request request = new Request(CoAP.Code.PUT, CoAP.Type.CON);
        request.setURI("coap:/" + uri);
        request.send(getReverseProxy().getUnicastEndpoint());
        LOGGER.info("setObservingQos - " + request);
        Response response;
        long timeout = WAIT_FACTOR;
        try {
            while(timeout < 5*WAIT_FACTOR){
                if(rtt == -1){
                    response = request.waitForResponse(500 * timeout);
                } else
                {
                    response = request.waitForResponse(rtt * timeout);
                }
                // receive the response

                if (response != null) {
                    LOGGER.info("Coap response received. - " + response);

                    // get RTO from the response

                    //TODO uncomment
                    //this.rtt = response.getRemoteEndpoint().getCurrentRTO();
                    break;
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
    }
    /**
     * Invokes the scheduler on the set of pending requests.
     * Produces a new scheduler schema.
     *
     * @return true if success, false otherwise.
     */
    private ScheduleResult schedule(){
        LOGGER.log(Level.FINER, "schedule()");
        long rtt = this.rtt;
        LOGGER.info("schedule() - Rtt: " + this.rtt);

        if(this.subscriberList.isEmpty()){
            return new ScheduleResult(0, rtt, false);
        }
        List<Task> tasks = new ArrayList<Task>();
        //TODO remove
        //dumpSubscribers();
        Set<ClientEndpoint> set = subscriberList.keySet();
        synchronized(this.subscriberList){
        	Iterator<ClientEndpoint> iterator = set.iterator(); 
            while (iterator.hasNext()){
            	ClientEndpoint ce = iterator.next();
	            Task tmp = new Task(ce);
	            InterfaceRequest old = this.subscriberList.get(ce);
	            tmp.setPmin(old.getPmin());
	            tmp.setPmax(old.getPmax());
	            tmp.setAllowed(old.isAllowed());
	            tmp.setExchange(old.getExchange());
	            tmp.setLastNotificationSent(old.getLastNotificationSent());
	            tmp.setOriginRequest(old.getOriginRequest());
	            tmp.setTimestampLastNotificationSent(old.getTimestampLastNotificationSent());
	            tasks.add(tmp);
            }
	        //TODO add max-age
	        double period = scheduler.schedule(tasks, rtt);
	
	
	        if(period > rtt){
	            for(Task t : tasks){
	                this.subscriberList.get(t.getClient()).setAllowed(true);
	            }
	            return new ScheduleResult(period, rtt, true);
	        }
	        return new ScheduleResult(period, rtt, false);
        }
    }

    /**
     * Invoked by the Resource Observer handler when a client cancel an observe subscription.
     *
     * @param clientEndpoint the Periodic Observing request that must be deleted
     */
    public  void deleteSubscriptionsFromClients(ClientEndpoint clientEndpoint) {
        LOGGER.log(Level.INFO, "deleteSubscriptionsFromClients(" + clientEndpoint + ")");
        if(clientEndpoint != null){
        	subscriberList.remove(clientEndpoint);

            if(subscriberList.isEmpty()){
                LOGGER.log(Level.INFO, "SubscriberList Empty");
                observeEnabled.set(false);
                /*lock.lock();
                newNotification.signalAll();
                lock.unlock();*/
                relation.reactiveCancel();
            } else{
                scheduleFeasibles();
            }
        }
    }


    public void setTimestamp(long timestamp) {
        LOGGER.log(Level.FINER, "setTimestamp(" + timestamp + ")");
        relation.getCurrent().advanced().setTimestamp(timestamp);
        // Update also Max Age to consider Server RTT
        LOGGER.info("MAX-AGE " + relation.getCurrent().advanced().getOptions().getMaxAge().toString());
        LOGGER.info("RTT " + rtt);
        //relation.getCurrent().advanced().getOptions().setMaxAge(relation.getCurrent().advanced().getOptions().getMaxAge() - (rtt / 1000));
    }

    
    /**
	 * This method is used to apply resource-specific knowledge on the exchange.
	 * If the request was successful, it sets the Observe option for the
	 * response. It is important to use the notificationOrderer of the resource
	 * here. Further down the layer, race conditions could cause local
	 * reordering of notifications. If the response has an error code, no
	 * observe relation can be established and if there was one previously it is
	 * canceled. When this resource allows to be observed by clients and the
	 * request is a GET request with an observe option, the
	 * {@link ServerMessageDeliverer} already created the relation, as it
	 * manages the observing endpoints globally.
	 * 
	 * @param exchange the exchange
	 * @param response the response
	 */
    @Override
	public void checkObserveRelation(Exchange exchange, Response response) {
		/*
		 * If the request for the specified exchange tries to establish an observer
		 * relation, then the ServerMessageDeliverer must have created such a relation
		 * and added to the exchange. Otherwise, there is no such relation.
		 * Remember that different paths might lead to this resource.
		 */
		
		InterfaceObserveRelation relation = (InterfaceObserveRelation) exchange.getRelation();
		if (relation == null) return; // because request did not try to establish a relation
		if (CoAP.ResponseCode.isSuccess(response.getCode())) {
			
			response.getOptions().setObserve(getNotificationOrderer().getCurrent());
			
			
			if (!relation.isEstablished()) {
				relation.setEstablished(true);
				addObserveRelation(relation);
				relation.setLastTimestamp(response.getTimestamp());
			} else if (getObserveType() != null) {
				// The resource can control the message type of the notification
				response.setType(getObserveType());
			}
		} // ObserveLayer takes care of the else case
	}
    
    /* (non-Javadoc)
	 * @see org.eclipse.californium.core.server.resources.Resource#addObserveRelation(org.eclipse.californium.core.observe.ObserveRelation)
	 */
	@Override
	public void addObserveRelation(ObserveRelation relation) {
		InterfaceObserveRelation interface_relation = (InterfaceObserveRelation) relation;
		interface_relation.setAllowed(true);
		if (observeRelations.add(relation)) {
			LOGGER.log(Level.INFO, "Replacing observe relation between {0} and resource {1}", new Object[]{relation.getKey(), getURI()});
		} else {
			LOGGER.log(Level.INFO, "Successfully established observe relation between {0} and resource {1}", new Object[]{relation.getKey(), getURI()});
		}
		for (ResourceObserver obs:getObservers())
			obs.addedObserveRelation(relation);
	}
}
