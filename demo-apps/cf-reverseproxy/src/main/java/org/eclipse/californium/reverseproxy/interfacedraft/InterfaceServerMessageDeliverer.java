package org.eclipse.californium.reverseproxy.interfacedraft;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveManager;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObservingEndpoint;
import org.eclipse.californium.core.server.MessageDeliverer;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.reverseproxy.interfacedraft.resources.InterfaceObserveRelation;
import org.eclipse.californium.reverseproxy.interfacedraft.resources.Parameters;
import org.eclipse.californium.reverseproxy.interfacedraft.resources.ReverseProxyResourceInterface;
import org.eclipse.californium.reverseproxy.resources.ClientEndpoint;

public class InterfaceServerMessageDeliverer implements MessageDeliverer{

	private static final Logger LOGGER = Logger.getLogger(InterfaceServerMessageDeliverer.class.getCanonicalName());

	/* The root of all resources */
	private final Resource root;

	/* The manager of the observe mechanism for this server */
	private final ObserveManager observeManager = new ObserveManager();

	/**
	 * Constructs a default message deliverer that delivers requests to the
	 * resources rooted at the specified root.
	 * 
	 * @param root the root resource
	 */
	public InterfaceServerMessageDeliverer(final Resource root) {
		this.root = root;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.californium.MessageDeliverer#deliverRequest(org.eclipse.californium.network.Exchange)
	 */
	@Override
	public void deliverRequest(final Exchange exchange) {
		Request request = exchange.getRequest();
		List<String> path = request.getOptions().getUriPath();
		final Resource resource = findResource(path);
		if (resource != null) {
			checkForObserveOption(exchange, resource);
			
			// Get the executor and let it process the request
			Executor executor = resource.getExecutor();
			if (executor != null) {
				exchange.setCustomExecutor();
				executor.execute(new Runnable() {
					public void run() {
						resource.handleRequest(exchange);
					} });
			} else {
				resource.handleRequest(exchange);
			}
		} else {
			LOGGER.log(Level.INFO, "Did not find resource {0} requested by {1}:{2}",
					new Object[]{path, request.getSource(), request.getSourcePort()});
			exchange.sendResponse(new Response(ResponseCode.NOT_FOUND));
		}
	}

	/**
	 * Checks whether an observe relationship has to be established or canceled.
	 * This is done here to have a server-global observeManager that holds the
	 * set of remote endpoints for all resources. This global knowledge is required
	 * for efficient orphan handling.
	 * 
	 * @param exchange
	 *            the exchange of the current request
	 * @param resource
	 *            the target resource
	 * @param path
	 *            the path to the resource
	 */
	private void checkForObserveOption(final Exchange exchange, final Resource resource) {
		Request request = exchange.getRequest();
		if (request.getCode() != Code.GET) {
			return;
		}

		InetSocketAddress source = new InetSocketAddress(request.getSource(), request.getSourcePort());

		if (request.getOptions().hasObserve() && resource.isObservable()) {
			
			if (request.getOptions().getObserve()==0) {
				// Requests wants to observe and resource allows it :-)
				LOGGER.log(Level.FINER,
						"Initiate an observe relation between {0}:{1} and resource {2}",
						new Object[]{request.getSource(), request.getSourcePort(), resource.getURI()});
				ObservingEndpoint remote = observeManager.findObservingEndpoint(source);
				ReverseProxyResourceInterface interface_resource = (ReverseProxyResourceInterface) resource;
				ClientEndpoint clientEndpoint = new ClientEndpoint(request.getSource(), request.getSourcePort());
				Parameters params = interface_resource.getParameters(clientEndpoint);
				ObserveRelation relation = new InterfaceObserveRelation(remote, resource, exchange, 
						params.getPmin(), params.getPmax(), params.isAllowed());
				remote.addObserveRelation(relation);
				exchange.setRelation(relation);
				// all that's left is to add the relation to the resource which
				// the resource must do itself if the response is successful
				
			} else if (request.getOptions().getObserve() == 1) {
				// Observe defines 1 for canceling
				ObserveRelation relation = observeManager.getRelation(source, request.getToken());
				if (relation != null) {
					relation.cancel();
				}
			}
		}
	}

	/**
	 * Searches in the resource tree for the specified path. A parent resource
	 * may accept requests to subresources, e.g., to allow addresses with
	 * wildcards like <code>coap://example.com:5683/devices/*</code>
	 * 
	 * @param list the path as list of resource names
	 * @return the resource or null if not found
	 */
	private Resource findResource(final List<String> list) {
		LinkedList<String> path = new LinkedList<String>(list);
		Resource current = root;
		while (!path.isEmpty() && current != null) {
			String name = path.removeFirst();
			current = current.getChild(name);
		}
		return current;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.californium.MessageDeliverer#deliverResponse(org.eclipse.californium.network.Exchange, org.eclipse.californium.coap.Response)
	 */
	@Override
	public void deliverResponse(Exchange exchange, Response response) {
		if (response == null) {
			throw new NullPointerException("Response must not be null");
		} else if (exchange == null) {
			throw new NullPointerException("Exchange must not be null");
		} else if (exchange.getRequest() == null) {
			throw new IllegalArgumentException("Exchange does not contain request");
		} else {
			exchange.getRequest().setResponse(response);
		}
	}
}
