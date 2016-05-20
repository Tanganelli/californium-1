package org.eclipse.californium.reverseproxy.interfacedraft;

import java.util.List;

/**
 * Created by giacomo on 13/05/16.
 */
public class Scheduler {

    /**
     * Produce the Periods to be used for registering Observing with end device.
     *
     * @param tasks the map of requests
     * @param rtt the RTT of the resource
     * @return the Periods to be used with the end device
     */
    public double schedule(List<Task> tasks, long rtt, long m){
        Task tminPmax = minPmax(tasks);
        double p = tminPmax.getPmax();
        boolean change = true;
        boolean greater = p >= rtt;
        while(change && greater){
            change = false;
            for(Task t : tasks){
                double pc = t.getPmax() / Math.ceil((t.getPmin()-m)/p);
                if(p > pc){
                    change = true;
                    p = pc;
                }
                if(p < rtt){
                    greater = false;
                    break;
                }
            }
        }
        if(!greater)
            return 0.0;
        return p;
    }

    /**
     * Return the task with the minimum pmax.
     *
     * @param tasks the pending tasks
     * @return the task with the minimum pmax
     */
    private Task minPmax(List<Task> tasks) {
        double minimum = Integer.MAX_VALUE;
        Task ret = null;
        for(Task t : tasks){
            if(minimum > t.getPmax()){
                minimum = t.getPmax();
                ret = t;
            }
        }
        return ret;
    }
}
