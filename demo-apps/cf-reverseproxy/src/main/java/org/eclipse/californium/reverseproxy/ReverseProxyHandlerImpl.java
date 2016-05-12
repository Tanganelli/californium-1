package org.eclipse.californium.reverseproxy;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.examples.ExampleReverseProxy;

/**
 * Reacts to discovery replies.
 */
public class ReverseProxyHandlerImpl implements ReverseProxyHandler{

    private ExampleReverseProxy reverseProxy;

    public ReverseProxyHandlerImpl(ExampleReverseProxy reverseProxy) {
        this.reverseProxy = reverseProxy;
    }

    @Override
    public void onLoad(Response response) {
        this.reverseProxy.receiveDiscoveryResponse(response);

    }

    @Override
    public void onError() {
        // TODO Auto-generated method stub

    }

}