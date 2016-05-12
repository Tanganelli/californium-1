package org.eclipse.californium.reverseproxy;

/**
 * Created by jacko on 12/05/16.
 */
public class Server{
    private String name;
    private String ip;
    private String port;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public String getPort() {
        return port;
    }
    public void setPort(String port) {
        this.port = port;
    }
}
