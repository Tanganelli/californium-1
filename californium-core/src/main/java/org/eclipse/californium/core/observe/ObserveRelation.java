package org.eclipse.californium.core.observe;

import java.net.InetSocketAddress;
import java.util.Iterator;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.Resource;

public interface ObserveRelation{
	
	/**
	 * Returns true if this relation has been established.
	 * @return true if this relation has been established
	 */
	public boolean isEstablished();
	
	/**
	 * Sets the established field.
	 *
	 * @param established true if the relation has been established
	 */
	public void setEstablished(boolean established);
	
	/**
	 * Cancel this observe relation. This methods invokes the cancel methods of
	 * the resource and the endpoint.
	 */
	public void cancel();
	
	/**
	 * Cancel all observer relations that this server has established with this'
	 * realtion's endpoint.
	 */
	public void cancelAll();
	
	/**
	 * Notifies the observing endpoint that the resource has been changed. This
	 * method makes the resource process the same request again.
	 */
	public void notifyObservers();
	
	/**
	 * Gets the resource.
	 *
	 * @return the resource
	 */
	public Resource getResource();
	
	/**
	 * Gets the exchange.
	 *
	 * @return the exchange
	 */
	public Exchange getExchange();
	
	/**
	 * Gets the source address of the observing endpoint.
	 *
	 * @return the source address
	 */
	public InetSocketAddress getSource();
	
	public boolean check();
	
	public Response getCurrentControlNotification();
	
	public void setCurrentControlNotification(Response recentControlNotification);
	
	public Response getNextControlNotification();
	
	public void setNextControlNotification(Response nextControlNotification);
	
	public void addNotification(Response notification);
	
	public Iterator<Response> getNotificationIterator();
	
	public String getKey();
	
	

}
