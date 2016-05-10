package io.logz.src;

import java.rmi.UnexpectedException;

/**
 * Created by roiravhon on 5/9/16.
 */

public class LogzioLogbackAppender extends ch.qos.logback.core.AppenderBase {

    // Data members
    LogzioSender logzioSender;

    // User controlled variables
    private String logzioToken;
    private int drainTimeout = 5000;
    private int fsPercentThreshold = 98;

    // Getters and Setters
    public void setToken(String logzioTokenParam) {
        logzioToken = logzioTokenParam;
    }

    public String getToken() {
        return logzioToken;
    }

    public void setDrainTimeout(int drainTimeoutParam) {

        // Basic protection from running negative timeout. 0 = no sleep at all.
        if (drainTimeoutParam < 0) {
            drainTimeout = 0;
        }
        else {
            drainTimeout = drainTimeoutParam;
        }
    }

    public int getDrainTimeout() {
        return drainTimeout;
    }

    public void setFsPercentThreshold(int fsPercentThresholdParam) {
        fsPercentThreshold = fsPercentThresholdParam;
    }

    public int getFsPercentThreshold() {
        return fsPercentThreshold;
    }

    @Override
    public void start() {

        if (logzioToken.isEmpty()) {

            System.out.println("Logz.io: Token is missing! Bailing out..");
            return;
        }

        try {
            logzioSender = new LogzioSender(logzioToken, drainTimeout, fsPercentThreshold);
            logzioSender.start();
        }
        catch (UnexpectedException e) {
            System.out.println("Logz.io: Something unexpected happened. Cant send logs. Bailing out..");
            e.printStackTrace();
            return;  // Not signaling super as up, we have something we cant deal with.
        }

        super.start();
    }

    @Override
    public void stop() {

        logzioSender.stop();
        super.stop();
    }

    @Override
    protected void append(Object o) {

        logzioSender.addToQueue(o);
    }
}
