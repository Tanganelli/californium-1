package org.eclipse.californium.reverseproxy.interfacedraft.resources;
import java.util.Date;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.reverseproxy.resources.ReverseProxyResource;

/**
 * Response Handler for notifications coming from the end device.
 */
public class ReverseProxyCoAPHandler implements CoapHandler{

    private ReverseProxyResourceInterface interface_resource;
    private int count = 0;
    /** The logger. */
    protected final static Logger LOGGER = Logger.getLogger(ReverseProxyResource.class.getCanonicalName());

    public ReverseProxyCoAPHandler(ReverseProxyResourceInterface ownerResource){
        this.interface_resource = ownerResource;
    }

    @Override
    public void onLoad(CoapResponse coapResponse) {
        if(coapResponse.getOptions().hasObserve()){
	        int observe_number = coapResponse.getOptions().getObserve();
	        interface_resource.getNotificationOrderer().setCurrent(observe_number);
        }
        //if(count==10)
        //	ownerResource.emulatedDelay = 11000;
        count++;
        LOGGER.info("new incoming notification: "+ count);
        Date now = new Date();
        long timestamp = now.getTime();
        interface_resource.setTimestamp(timestamp);
        InterfaceObserveRelationFilter filter = new InterfaceObserveRelationFilter(interface_resource);

        this.interface_resource.changed(filter);

    }

    @Override
    public void onError() {
        // TODO Auto-generated method stub

    }

}
