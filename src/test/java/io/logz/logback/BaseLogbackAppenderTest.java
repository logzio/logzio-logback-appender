package io.logz.logback;

import ch.qos.logback.core.Context;
import io.logz.test.MockLogzioBulkListener;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author MarinaRazumovsky
 */
public class BaseLogbackAppenderTest {

    private final static Logger logger = LoggerFactory.getLogger(BaseLogbackAppenderTest.class);

    protected MockLogzioBulkListener mockListener;

    @Before
    public void startMockListener() throws Exception {
        mockListener = new io.logz.test.MockLogzioBulkListener();
        mockListener.start();
    }

    @After
    public void stopMockListener() {
        mockListener.stop();
    }

    protected Logger createLogger(String token, String type, String loggerName, Integer drainTimeout,
                                  boolean addHostname,boolean line, String additionalFields, boolean compressRequests) {

        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}, addHostname={}, line={}, additionalFields={} ",
                loggerName, token, type, drainTimeout, addHostname, line, additionalFields);

        ch.qos.logback.classic.Logger logbackLogger =  (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        Context logbackContext = logbackLogger.getLoggerContext();
        LogzioLogbackAppender logzioLogbackAppender = new LogzioLogbackAppender();
        logzioLogbackAppender.setContext(logbackContext);
        logzioLogbackAppender.setToken(token);
        logzioLogbackAppender.setLogzioType(type);
        logzioLogbackAppender.setDebug(true);
        logzioLogbackAppender.setLine(line);
        logzioLogbackAppender.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioLogbackAppender.setAddHostname(addHostname);
        logzioLogbackAppender.setCompressRequests(compressRequests);
        logzioLogbackAppender.setName("LogzioLogbackAppender");
        if (drainTimeout != null) {
            logzioLogbackAppender.setDrainTimeoutSec(drainTimeout);
        }
        if (additionalFields != null) {
            logzioLogbackAppender.setAdditionalFields(additionalFields);
        }
        logzioLogbackAppender.start();
        assertThat(logzioLogbackAppender.isStarted()).isTrue();
        logbackLogger.addAppender(logzioLogbackAppender);
        logbackLogger.setAdditive(false);
        return logbackLogger;
    }

    protected Logger createLogger(String token, String type, String loggerName, Integer drainTimeout,
                                  boolean addHostname,boolean line, String additionalFields) {
        return createLogger(token, type, loggerName, drainTimeout, addHostname, line, additionalFields, false);
    }

    protected void sleepSeconds(int seconds) {
        logger.info("Sleeping {} [sec]...", seconds);
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected String random(int numberOfChars) {
        return UUID.randomUUID().toString().substring(0, numberOfChars-1);
    }
}
