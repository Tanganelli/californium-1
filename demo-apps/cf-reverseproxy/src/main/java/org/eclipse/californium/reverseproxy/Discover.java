package org.eclipse.californium.reverseproxy;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Endpoint;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The discover Thread that issues periodic discovery queries to end devices.
 */
public class Discover implements Runnable {
    private Endpoint endpoint;
    private ReverseProxyHandler handler;
    private List<Server> serverList;
    private String name;
    private final static Logger LOGGER = Logger.getLogger(Worker.class.getCanonicalName());

    public Discover(String name, Endpoint endpoint, ReverseProxyHandler handler, String serversConfig) {
        setName(name);
        this.endpoint = endpoint;
        this.handler = handler;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        Server currServer = null;
        try {
            FileReader file = new FileReader(serversConfig);
            XMLStreamReader reader = factory.createXMLStreamReader(file);
            String tagContent = "";
            while(reader.hasNext()){
                int event = reader.next();

                switch(event){
                    case XMLStreamConstants.START_ELEMENT:
                        if ("server".equals(reader.getLocalName())){
                            currServer = new Server();
                        }
                        if("servers".equals(reader.getLocalName())){
                            serverList = new ArrayList<Server>();
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        tagContent = reader.getText().trim();
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        if(reader.getLocalName().equals("server"))
                            serverList.add(currServer);
                        if(reader.getLocalName().equals("name") && currServer != null)
                            currServer.setName(tagContent);
                        if(reader.getLocalName().equals("ip") && currServer != null)
                            currServer.setIp(tagContent);
                        if(reader.getLocalName().equals("port") && currServer != null)
                            currServer.setPort(tagContent);
                        break;

                    case XMLStreamConstants.START_DOCUMENT:
                        serverList = new ArrayList<Server>();
                        break;

                }
            }
        } catch (XMLStreamException | FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run() {
        LOGGER.log(Level.FINE, "Starting worker [{0}]", name);
        try {
            for(Server server : serverList){
                Request request = new Request(CoAP.Code.GET, CoAP.Type.CON);
                request.addMessageObserver(new ReverseProxyDiscoveryMessageObserver(handler));
                try {
                    request.setDestination(InetAddress.getByName(server.getIp()));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                request.setDestinationPort(Integer.parseInt(server.getPort()));
                request.getOptions().setUriPath("/.well-known/core");
                request.send(this.endpoint);
            }
        } catch (Throwable t) {
                LOGGER.log(Level.FINE, "Worker [{0}] exception", getName());
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}