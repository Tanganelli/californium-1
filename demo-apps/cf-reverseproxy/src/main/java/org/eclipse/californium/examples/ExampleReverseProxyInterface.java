package org.eclipse.californium.examples;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.reverseproxy.interfacedraft.resources.ReverseProxyResourceInterface;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by jacko on 16/05/16.
 */
public class ExampleReverseProxyInterface extends ExampleReverseProxy {
    public ExampleReverseProxyInterface(String config, String ip) {
        super(config, ip);
    }

    /**
     * Parse the discovery response and fill the resource tree of the proxy.
     *
     * @param response the discovery response
     */
    @Override
    public synchronized void receiveDiscoveryResponse(Response response) {

        if (response.getOptions().getContentFormat()!= MediaTypeRegistry.APPLICATION_LINK_FORMAT)
            return;

        // parse
        Set<WebLink> resources = LinkFormat.parse(response.getPayloadString());
        InetSocketAddress source = new InetSocketAddress(response.getSource(), response.getSourcePort());
        Set<WebLink> to_add = new HashSet<WebLink>();
        for(WebLink l : resources){
            if(!l.getURI().equals("/.well-known/core")){
                to_add.add(l);
            }
        }
        
        Set<WebLink> ret = mapping.put(source, to_add);
        for(Map.Entry<InetSocketAddress, Set<WebLink>> sa : mapping.entrySet()){
            for(WebLink l : sa.getValue()){
            	if(ret == null || !containsURI(ret, l)){
	                try {
	                    URI uri = new URI("coap://"+sa.getKey().toString().substring(1)+l.getURI());
	                    Resource res = new CoapResource(sa.getKey().toString().substring(1));
	                    
	                    this.add(res);
	                    Resource res2 = new ReverseProxyResourceInterface(l.getURI().substring(1), uri, l.getAttributes(), this.getUnicastEndpoint().getConfig(), this);
	                    res.add(res2);
	                } catch (URISyntaxException e) {
	                    System.err.println("Invalid URI: " + e.getMessage());
	
	                }
            	}
            }
        }
    }

    private boolean containsURI(Set<WebLink> ret, WebLink l) {
		for(WebLink r : ret){
			if(r.getURI().equals(l.getURI()))
				return true;
		}
		return false;
	}

	public static void main(String[] args) throws SocketException {

        // create server
        ExampleReverseProxyInterface proxy = new ExampleReverseProxyInterface("/home/jacko/workspace/californium/servers.xml", "127.0.0.1");
        proxy.start();

    }
}
