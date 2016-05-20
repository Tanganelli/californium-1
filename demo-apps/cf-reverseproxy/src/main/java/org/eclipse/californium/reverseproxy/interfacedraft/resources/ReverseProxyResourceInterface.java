package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.ResourceAttributes;
import org.eclipse.californium.examples.ExampleReverseProxy;
import org.eclipse.californium.reverseproxy.interfacedraft.*;
import org.eclipse.californium.reverseproxy.resources.ClientEndpoint;
import org.eclipse.californium.reverseproxy.resources.ReverseProxyResource;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class ReverseProxyResourceInterface extends ReverseProxyResource {
    /** The factor that multiplied for the actual RTT
     * is used as the timeout for waiting replies from the end device.*/
    private static long WAIT_FACTOR = 10;

    public InterfaceObserveRelationContainer getObserveRelations() {
        return observeRelations;
    }

    /* The the list of CoAP observe relations. */
    private InterfaceObserveRelationContainer observeRelations;

    public double getNotificationPeriod() {
        return notificationPeriod;
    }

    public CoapObserveRelation getRelation() {
        return relation;
    }

    private double notificationPeriod;

    public void setRtt(long rtt) {
        this.rtt = rtt;
    }

    private long rtt;

    public long getLastValidRtt() {
        return lastValidRtt;
    }

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

    public ReverseProxyResourceInterface(String name, URI uri, ResourceAttributes resourceAttributes, NetworkConfig networkConfig, ExampleReverseProxy reverseProxy) {
        super(name, uri, resourceAttributes, networkConfig, reverseProxy);
        this.rtt = 500;
        subscriberList = new HashMap<ClientEndpoint, InterfaceRequest>();
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
            addSubscriber(clientEndpoint, interface_request);
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
            int ret = handleGETCoRE(exchange);
            if(ret == 0){
                // create Observe request for the first client
                if(observeEnabled.compareAndSet(false, true)){
                    relation = client.observe(new ReverseProxyCoAPHandler(this));
                    Response responseForClients = getLast(request);
                    Date now = new Date();
                    long timestamp = now.getTime();
                    updateSubscriberNotification(new ClientEndpoint(request.getSource(), request.getSourcePort()), timestamp, relation.getCurrent().advanced());
                    exchange.respond(responseForClients);
                    LOGGER.info("Start Notification Task");
                    notificationExecutor.submit(notificationTask);
                    rttExecutor.submit(rttTask);
                }else{
                    //reply to client
                    Date now = new Date();
                    long timestamp = now.getTime();
                    ClientEndpoint clientEndpoint = new ClientEndpoint(request.getSource(), request.getSourcePort());
                    InterfaceRequest interface_request = getSubscriberCopy(clientEndpoint);
                    long elapsed = timestamp - interface_request.getTimestampLastNotificationSent();
                    if(interface_request.getPmin() <= elapsed || interface_request.getTimestampLastNotificationSent() == -1)
                    {
                        Response responseForClients = getLast(request);
                        //save lastNotification for the client
                        updateSubscriberNotification(new ClientEndpoint(request.getSource(), request.getSourcePort()), timestamp, relation.getCurrent().advanced());
                        exchange.respond(responseForClients);
                        lock.lock();
                        newNotification.signalAll();
                        lock.unlock();
                    }

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

    public synchronized Map<ClientEndpoint, InterfaceRequest> getSubscriberListCopy() {
        LOGGER.log(Level.FINER, "getSubscriberList()");
        return this.subscriberList;
    }

    private synchronized void updateSubscriberNotification(ClientEndpoint clientEndpoint,
                                                           long timestamp, Response response) {
        LOGGER.log(Level.FINER, "updateSubscriberNotification(" + clientEndpoint+ ", "+ timestamp+", "+response+")");
        if(this.subscriberList.containsKey(clientEndpoint)){
            this.subscriberList.get(clientEndpoint).setTimestampLastNotificationSent(timestamp);
            this.subscriberList.get(clientEndpoint).setLastNotificationSent(response);
        }
    }

    private synchronized InterfaceRequest getSubscriberCopy(ClientEndpoint clientEndpoint) {
        LOGGER.log(Level.INFO, "getSubscriberCopy(" + clientEndpoint + ")");
        return this.subscriberList.get(clientEndpoint);
    }

    private synchronized void addSubscriber(ClientEndpoint clientEndpoint, InterfaceRequest pr) {
        LOGGER.log(Level.FINER, "addSubscriber(" + clientEndpoint+ ", "+ pr +")");
        this.subscriberList.put(clientEndpoint, pr);
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
        InterfaceRequest interface_request = getSubscriberCopy(clientEndpoint);
        if(interface_request == null)
            interface_request = new InterfaceRequest();

        // Both parameters have been set
        if(interface_request.getPmin() != -1 && interface_request.getPmax() != -1){
            if(interface_request.isAllowed() || scheduleNewRequest(interface_request)){
                interface_request.setExchange(exchange);
                interface_request.setOriginRequest(request);
                addSubscriber(clientEndpoint, interface_request);
                return 0;
            } else{
                // Scheduling is not feasible
                removeSubscriber(clientEndpoint);
                // 1 = CoAP.ResponseCode.NOT_ACCEPTABLE
                return 1;
            }
        }
        else{
            //No parameter has been set
			/* TODO Decide what to do in the case of an observing relationship where the client didn't set any parameter
			 * Now we stop it and reply with an error.
			 */
            removeSubscriber(clientEndpoint);
            // 2 = CoAP.ResponseCode.FORBIDDEN
            return 2;
        }
    }

    /**
     * Verify if the new request can be accepted.
     *
     */
    private boolean scheduleNewRequest(InterfaceRequest params) {
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
        lock.lock();
        try {
            while(relation == null || relation.getCurrent() == null)
                newNotification.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally{
            lock.unlock();
        }
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
    protected void scheduleFeasibles() {
        LOGGER.log(Level.INFO, "scheduleFeasibles()");
        boolean end = false;
        while(!end) // delete the most demanding client
        {
            dumpSubscribers();
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
        Map<ClientEndpoint, InterfaceRequest> tmp = getSubscriberListCopy();
        for(Map.Entry<ClientEndpoint, InterfaceRequest> entry : tmp.entrySet()){
            if(entry.getValue().getPmax() < minPmax){
                minPmax = entry.getValue().getPmax();
                ret = entry.getKey();
            }
        }
        return ret;
    }

    private void deleteSubscriptionFromProxy(ClientEndpoint client) {
        LOGGER.log(Level.INFO, "deleteSubscriptionFromProxy(" + client + ")");
        InterfaceRequest invalid = getSubscriberCopy(client);
		/*invalid.setAllowed(false);
		addInvalidSubscriber(client, invalid);*/
        removeSubscriber(client);
        Response response = getLast(invalid.getOriginRequest());
        response.getOptions().removeObserve();
        ObserveRelation rel = invalid.getExchange().advanced().getRelation();
        invalid.getExchange().advanced().setRelation(null);
        invalid.getExchange().respond(response);
        rel.cancel();
    }

    private synchronized void removeSubscriber(ClientEndpoint clientEndpoint) {
        LOGGER.log(Level.INFO, "removeSubscriber(" + clientEndpoint + ")");
        this.subscriberList.remove(clientEndpoint);
        dumpSubscribers();
    }

    private boolean updatePeriods(ScheduleResult ret) {
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
    private void setObservingQoS() {
        LOGGER.log(Level.INFO, "setObserving()");
        double period = (this.notificationPeriod) / 1000; // convert to second
        String uri = this.getURI()+"?period="+ period;
        Request request = new Request(CoAP.Code.PUT, CoAP.Type.CON);
        request.setURI(uri);
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
    private synchronized ScheduleResult schedule(){
        LOGGER.log(Level.FINER, "schedule()");
        long rtt = this.rtt;
        LOGGER.info("schedule() - Rtt: " + this.rtt);

        if(this.subscriberList.isEmpty()){
            return new ScheduleResult(0, rtt, false);
        }
        List<Task> tasks = new ArrayList<Task>();
        //TODO remove
        dumpSubscribers();
        for(ClientEndpoint ce : this.subscriberList.keySet()){
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
        double period = scheduler.schedule(tasks, rtt, 60);


        if(period > rtt){
            for(Task t : tasks){
                this.subscriberList.get(t.getClient()).setAllowed(true);
            }
            return new ScheduleResult(period, rtt, true);
        }
        return new ScheduleResult(period, rtt, false);
    }

    private void dumpSubscribers() {
        LOGGER.log(Level.INFO, "dumpSubscribers()");
        for(Map.Entry<ClientEndpoint, InterfaceRequest> entry : this.subscriberList.entrySet()){
            LOGGER.info(entry.getKey().toString() + " " + entry.getValue().toString());
        }

    }

    /**
     * Invoked by the Resource Observer handler when a client cancel an observe subscription.
     *
     * @param clientEndpoint the Periodic Observing request that must be deleted
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


    public void setTimestamp(long timestamp) {
        LOGGER.log(Level.FINER, "setTimestamp(" + timestamp + ")");
        relation.getCurrent().advanced().setTimestamp(timestamp);
        // Update also Max Age to consider Server RTT
        LOGGER.info("MAX-AGE " + relation.getCurrent().advanced().getOptions().getMaxAge().toString());
        LOGGER.info("RTT " + rtt);
        relation.getCurrent().advanced().getOptions().setMaxAge(relation.getCurrent().advanced().getOptions().getMaxAge() - (rtt / 1000));
    }

}
