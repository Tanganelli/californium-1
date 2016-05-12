package org.eclipse.californium.reverseproxy;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by jacko on 12/05/16.
 */
public abstract class Worker extends Thread {

    private Random rnd;
    private final static Logger LOGGER = Logger.getLogger(Worker.class.getCanonicalName());
    private boolean running;
    /**
     * Instantiates a new worker.
     *
     * @param name the name
     */
    public Worker(String name) {
        super(name);
        setDaemon(true);
        rnd = new Random();
        setRunning(true);
    }

    /* (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    public void run() {
        LOGGER.log(Level.FINE, "Starting worker [{0}]", getName());
        while (running) {
            try {
                work();
                //TODO decide what to do
                //Thread.sleep(MULTICAST_SLEEP + rnd.nextInt(MULTICAST_SLEEP));
                break;
            } catch (Throwable t) {
                if (running)
                    LOGGER.log(Level.WARNING, "Exception occurred in Worker [" + getName() + "] (running="
                            + running + "): ", t);
                else
                    LOGGER.log(Level.FINE, "Worker [{0}] has been stopped successfully", getName());
            }
        }
    }

    /**
     * @throws Exception the exception to be properly logged
     */
    protected abstract void work() throws Exception;

    public void setRunning(boolean running) {
        this.running = running;
    }
}
