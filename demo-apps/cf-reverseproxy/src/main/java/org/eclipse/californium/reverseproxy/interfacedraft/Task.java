package org.eclipse.californium.reverseproxy.interfacedraft;

import org.eclipse.californium.reverseproxy.resources.ClientEndpoint;

/**
 * Created by giacomo on 13/05/16.
 */
public class Task extends InterfaceRequest {
    public ClientEndpoint getClient() {
        return client;
    }

    private ClientEndpoint client;

    public Task(ClientEndpoint client){
        super();
        this.client=client;
    }
}
