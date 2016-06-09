package org.eclipse.californium.examples;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by jacko on 16/05/16.
 */
public class InterfaceServer extends CoapServer {
    public static void main(String[] args) {
        int port = 5683;
        if (args.length == 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Argument" + args[0] + " must be an integer.");
                System.exit(1);
            }

        }
        try {

            // create server
            InterfaceServer server = new InterfaceServer();
            // add endpoints on all IP addresses
            server.addEndpoints(port);
            server.start();

        } catch (SocketException e) {
            System.err.println("Failed to initialize server: " + e.getMessage());
        }
    }

    /**
     * Add individual endpoints listening on default CoAP port on all IPv4 addresses of all network interfaces.
     */
    private void addEndpoints(int port) {
        for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
            // only binds to IPv4 addresses and localhost
            if (addr instanceof Inet4Address || addr.isLoopbackAddress()) {
                InetSocketAddress bindToAddress = new InetSocketAddress(addr, port);
                addEndpoint(new CoapEndpoint(bindToAddress));
            }
        }
    }

    /*
     * Constructor for a new Hello-World server. Here, the resources
     * of the server are initialized.
     */
    public InterfaceServer() throws SocketException {

        // provide an instance of a Hello-World resource
        add(new InterfaceResource());
    }

    /*
     * Definition of the Hello-World Resource
     */
    class InterfaceResource extends CoapResource {

        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // The current time represented as string
        private String time;
        private int notificationPeriod = 5; // 5 seconds as default
        private int maxAge = 60; // 60 seconds as default
        private DynamicTimeTask task;
        private Lock lock;
        private Condition newPeriod;
        public InterfaceResource() {

            // set resource identifier
            super("InterfaceResource");
            setObservable(true);
            // set display name
            getAttributes().setTitle("Interface Resource");
            getAttributes().addResourceType("observe");
            getAttributes().setObservable();
            setObserveType(CoAP.Type.NON);
            task = null;
            lock = new ReentrantLock();
            newPeriod = lock.newCondition();
        }
        private class DynamicTimeTask extends Thread {

            private boolean exit;

            public DynamicTimeTask(){
                exit = false;
            }

            @Override
            public void run() {
                while(!exit){
                    time = getTime();

                    // Call changed to notify subscribers
                    changed();
                    //notifyObserverRelations();
                    LOGGER.info("Send Notification");
                    lock.lock();
                    try {
                        newPeriod.await(notificationPeriod, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        LOGGER.info("Stop Thread");
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }

        private String getTime() {
            DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            Date time = new Date();
            return dateFormat.format(time);
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            exchange.setMaxAge(maxAge);
            // respond to the request
            exchange.respond("CoRE Interface Resource Value");
        }

        @Override
        public void handlePUT(CoapExchange exchange){
            Request request = exchange.advanced().getRequest();
            LOGGER.info("Received Request " + request);
            List<String> queries = request.getOptions().getUriQuery();
            if(!queries.isEmpty()){
                for(String composedquery : queries){
                    //handle queries values
                    String[] tmp = composedquery.split("=");
                    if(tmp.length != 2) // not valid period
                        return;
                    String query = tmp[0];
                    String value = tmp[1];
                    if(query.equals("period")){
                        int seconds;
                        try{
                            Double period = (Double.parseDouble(value) * 1000);
                            System.out.println("Period: " + period);
                            seconds = period.intValue() ;
                            if(seconds <= 0) throw new NumberFormatException();
                            notificationPeriod = seconds;
                        } catch(NumberFormatException e){
                            Response response = new Response(CoAP.ResponseCode.BAD_REQUEST);
                            response.setDestination(request.getSource());
                            response.setDestinationPort(request.getDestinationPort());
                            exchange.advanced().sendResponse(response);
                            return;
                        }
                    }
                }
                //maxAge = max_period;
                System.out.println("Notification Period = " + this.notificationPeriod);
                if(task != null){
                    lock.lock();
                    newPeriod.signal();
                    lock.unlock();
                } else {
                    task = new DynamicTimeTask();
                    task.start();
                }
            }
            exchange.respond(CoAP.ResponseCode.CHANGED);
        }
    }

}
