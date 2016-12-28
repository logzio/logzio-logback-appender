package io.logz;

import io.logz.sender.ILogzioStatusReporter;
import io.logz.sender.LogzioSender;

import java.util.Collections;
import java.util.concurrent.Executors;

/**
 * Created by MarinaRazumovsky on 26/12/2016.
 */

public class SimpleTestSender implements ILogzioStatusReporter {

    private LogzioSender<SimpleTestMessage> logzioSender;


    public SimpleTestSender(String token, String type, int drainTimeout, int fsThreshold, String bufferDir,
                            String logzioUrl, int socketTimeout, int connectTimeout, boolean debug, int interval) {
        logzioSender = LogzioSender.getOrCreateSenderByType(token, type, drainTimeout,fsThreshold, bufferDir,
                logzioUrl, socketTimeout, connectTimeout, debug, this, Executors.newScheduledThreadPool(2),interval,
                new SimpleTestMessage.TestLogzioMessageFormatter("", false, this, Collections.emptySet() ) );
        logzioSender.start();
    }

    public void sendMessage(SimpleTestMessage message) {
        logzioSender.send(message);
    }

    @Override
    public void error(String msg) {
        System.out.println("ERROR: "+msg);
    }

    @Override
    public void error(String msg, Throwable e) {
        System.out.println("ERROR: "+msg);
        e.printStackTrace();
    }

    @Override
    public void warning(String msg) {
        System.out.println("WARNING: "+msg);
    }

    @Override
    public void warning(String msg, Throwable e) {
        System.out.println("WARNING: "+msg);
        e.printStackTrace();
    }

    @Override
    public void info(String msg) {
        System.out.println("INFO: "+msg);
    }

    @Override
    public void info(String msg, Throwable e) {
        System.out.println("INFO: "+msg);
        e.printStackTrace();
    }

    public void stop(){
        logzioSender.stop();
    }
}
