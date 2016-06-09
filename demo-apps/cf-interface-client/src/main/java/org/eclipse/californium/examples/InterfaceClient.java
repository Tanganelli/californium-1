package org.eclipse.californium.examples;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;




public class InterfaceClient {

	private static final Logger LOGGER = Logger.getLogger(InterfaceClient.class.getCanonicalName());
	private static Handler loggingHandler;
	/**
	 * Main entry point.
	 * 
	 * @param args the arguments
	 */
	public static void main(String[] args) {

		CLI cli = new CLI(args);
		cli.parse();
		String uri = cli.getUri();
		double pmax = cli.getPmax();
		double pmin = cli.getPmin();
		int stop = cli.getStopCount();
		String logFile = cli.getLogFile();
		String ip = cli.getIp();
		Lock lock = new ReentrantLock();
		Condition notEnd = lock.newCondition();
		/*try {
			loggingHandler = new FileHandler(logFile);
			LOGGER.addHandler(loggingHandler);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		String putUri = uri + "?pmin=" + String.valueOf(pmin) + "&pmax=" + String.valueOf(pmax);
		
		// re-usable response object
		CoapResponse response;
		InterfaceCoAPHandler handler = new InterfaceCoAPHandler(pmin, pmax, stop, logFile, notEnd, lock);
		
		CoapClient client = new CoapClient();
		client.setTimeout(0);
		if(ip != null){
			try {
				InetSocketAddress address =  new InetSocketAddress(InetAddress.getByName(ip), 0);
				Endpoint endpoint = new CoapEndpoint(address);
				client.setEndpoint(endpoint);
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
		try{

				client.setURI(putUri);
				response = client.put("", MediaTypeRegistry.UNDEFINED);
				if(!response.isSuccess())
					System.exit(0);
				client.setURI(uri);
				Thread.sleep(1000);
				CoapObserveRelation relation = client.observe(handler);
				try {
					lock.lock();
					while(handler.getNotificationsCount() < stop && !handler.isExit())
						notEnd.await();
					relation.proactiveCancel();
					
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					lock.unlock();
				}
				//relation.proactiveCancel();
				LOGGER.info("Missed Deadlines: "+ handler.getMissDeadlines() + ", Missed Gaps: "+ handler.getMissGaps() +", TotalNotifications: "+ handler.getNotificationsCount());
				System.out.println(getNow() + "INFO - Missed Deadlines: "+ handler.getMissDeadlines() + ", TotalNotifications: "+ handler.getNotificationsCount());
			
		} catch (NullPointerException e)
		{
			e.printStackTrace();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.exit(0);
	}
	
	private static String getNow() {
		Date now = new Date();
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSSS ");
		return dateFormat.format(now);
	}
}
