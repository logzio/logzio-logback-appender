package io.logz.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.rmi.UnexpectedException;

/**
 * Created by roiravhon on 5/9/16.
 */

public class LogzioLogbackAppender extends AppenderBase<ILoggingEvent> {

    // Data members
    LogzioSender logzioSender;

    // User controlled variables
    private String logzioToken;
    private String logzioType = "java";
    private int drainTimeout = 5;
    private int fsPercentThreshold = 98;
    private String bufferDir;
    private String logzioUrl = "https://listener.logz.io:8071";


    // Getters and Setters
    public void setToken(String logzioToken) {this.logzioToken = logzioToken;}
    public String getToken() {return logzioToken;}

    public void setLogzioType(String logzioType) {this.logzioType = logzioType;}
    public String getLogzioType() {return logzioType;}

    public void setDrainTimeout(int drainTimeout) {

        // Basic protection from running negative timeout. 0 = no sleep at all.
        if (drainTimeout < 0) {
            this.drainTimeout = 0;
        }
        else {
            this.drainTimeout = drainTimeout;
        }
    }
    public int getDrainTimeout() {return drainTimeout;}

    public void setFsPercentThreshold(int fsPercentThreshold) {this.fsPercentThreshold = fsPercentThreshold;}
    public int getFsPercentThreshold() {return fsPercentThreshold;}

    public void setBufferDir(String bufferDir) {this.bufferDir = bufferDir;}
    public String getBufferDir() {return bufferDir;}

    public void setLogzioUrl(String logzioUrl) {this.logzioUrl = logzioUrl;}
    public String getLogzioUrl() {return logzioUrl;}

    @Override
    public void start() {

        if (logzioToken == null) {

            System.out.println("Logz.io: Token is missing! Bailing out..");
            return;
        }

        try {
            logzioSender = new LogzioSender(logzioToken, logzioType, drainTimeout, fsPercentThreshold, bufferDir, logzioUrl);
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
    protected void append(ILoggingEvent iLoggingEvent) {

        logzioSender.addToQueue(iLoggingEvent);
    }
}
