package org.eclipse.californium.reverseproxy.interfacedraft;

/**
 * Created by giacomo on 13/05/16.
 */
public class ScheduleResult {

    private long lastRtt;
    private boolean valid;
    private double period;

    public ScheduleResult(double period,long rtt, boolean valid){
        setLastRtt(rtt);
        setPeriod(period);
        setValid(valid);
    }

    public long getLastRtt() {
        return lastRtt;
    }

    public void setLastRtt(long lastRtt) {
        this.lastRtt = lastRtt;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public double getPeriod() {
        return period;
    }

    public void setPeriod(double period) {
        this.period = period;
    }
}
