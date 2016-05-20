/**
 * Created by jacko on 12/05/16.
 */
package org.eclipse.californium.examples;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.*;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.reverseproxy.Discover;
import org.eclipse.californium.reverseproxy.ReverseProxyHandler;
import org.eclipse.californium.reverseproxy.ReverseProxyHandlerImpl;
import org.eclipse.californium.reverseproxy.resources.ReverseProxyResource;
import org.eclipse.californium.core.network.CoapEndpoint;

import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExampleReverseProxy extends CoapServer {

    /** The logger. */
    private final static Logger LOGGER = Logger.getLogger(CoapServer.class.getCanonicalName());

    private static final int MULTICAST_SLEEP = 10000; //from 10 sec to 20 sec

    public Endpoint multicastEndpointIPv4;
    private Endpoint multicastEndpointIPv6;
    private Endpoint unicastEndpoint;
    private Runnable discoverThreadIPv4;
    private ScheduledFuture<?> discoverHandle;
    private Thread discoverThreadIPv6;
    private ReverseProxyHandler handlerIPv4;
    private ReverseProxyHandler handlerIPv6;

    private boolean running;

    protected Map<InetSocketAddress, Set<WebLink>> mapping;

    private Map<InetSocketAddress, Long> clientRTT;

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
    private final ScheduledExecutorService discoverScheduler = Executors.newScheduledThreadPool(1);

    public ExampleReverseProxy(String config, String ip){
        handlerIPv4 = new ReverseProxyHandlerImpl(this);
        try {
            InetSocketAddress address =  new InetSocketAddress(InetAddress.getByName(ip), 5683);
            setUnicastEndpoint(new CoapEndpoint(address));
            this.addEndpoint(getUnicastEndpoint());
            this.discoverThreadIPv4 = new Discover("UDP-Discover-"+getUnicastEndpoint().getAddress().getHostName(), this.getUnicastEndpoint(), this.handlerIPv4, config);
            mapping = new HashMap<InetSocketAddress, Set<WebLink>>();
            clientRTT = new HashMap<InetSocketAddress, Long>();
            setExecutor(executor);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


    }

    @Override
    public void start(){
        LOGGER.info("Starting ReverseProxy");
        super.start();
        try{
            discoverHandle = discoverScheduler.scheduleAtFixedRate(this.discoverThreadIPv4, 10, 10, TimeUnit.SECONDS);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop(){
        if (running){
            this.running = false;
            try{
                discoverHandle.cancel(true);
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        super.stop();
    }

    /**
     * Parse the discovery response and fill the resource tree of the proxy.
     *
     * @param response the discovery response
     */
    public synchronized void receiveDiscoveryResponse(Response response) {
        LOGGER.log(Level.INFO, "Discovery Response [{0}]", response);
        if (response.getOptions().getContentFormat()!= MediaTypeRegistry.APPLICATION_LINK_FORMAT)
            return;

        // parse
        Set<WebLink> resources = LinkFormat.parse(response.getPayloadString());
        Set<WebLink> to_add = new HashSet<WebLink>();
        for(WebLink l : resources){
            if(!l.getURI().equals("/.well-known/core")){
                to_add.add(l);
            }
        }
        InetSocketAddress source = new InetSocketAddress(response.getSource(), response.getSourcePort());
        Set<WebLink> ret = mapping.put(source, to_add);
        for(Map.Entry<InetSocketAddress, Set<WebLink>> sa : mapping.entrySet()){
            for(WebLink l : sa.getValue()){
            	if(!ret.contains(l)){
	                try {
	                    URI uri = new URI("coap://"+sa.getKey().toString().substring(1)+l.getURI());
	                    Resource res = new CoapResource(sa.getKey().toString().substring(1));
	                    this.add(res);
	                    Resource res2 = new ReverseProxyResource(l.getURI().substring(1), uri, l.getAttributes(), this.getUnicastEndpoint().getConfig(), this);
	                    res.add(res2);
	                } catch (URISyntaxException e) {
	                    System.err.println("Invalid URI: " + e.getMessage());
	
	                }
            	}
            }
        }
    }

    public void addClientRTT(InetAddress ip, int port, long rtt) {
        try {
            InetSocketAddress key = new InetSocketAddress(InetAddress.getByName(ip.getHostAddress()), port);
            clientRTT.put(key, rtt);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public long getClientRTT(InetAddress ip, int port) {
        InetSocketAddress key;
        try {
            key = new InetSocketAddress(InetAddress.getByName(ip.getHostAddress()), port);
            if(clientRTT.containsKey(key))
                return clientRTT.get(key);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0;
    }

    public Endpoint getUnicastEndpoint() {
        return unicastEndpoint;
    }

    public void setUnicastEndpoint(Endpoint unicastEndpoint) {
        this.unicastEndpoint = unicastEndpoint;
    }

    public static void main(String[] args) throws SocketException {

        // create server
        ExampleReverseProxy proxy = new ExampleReverseProxy("/home/jacko/workspace/californium/servers.xml", "127.0.0.1");
        proxy.start();

    }
}

