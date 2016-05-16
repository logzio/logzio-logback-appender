package io.logz.src;


import ch.qos.logback.classic.Level;
import ch.qos.logback.core.Context;
import static org.junit.Assert.*;
import com.sun.istack.internal.Nullable;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Created by roiravhon on 5/15/16.
 */
public class LogzioSenderTest {

    private final static Logger logger = LoggerFactory.getLogger(MockHttpServer.class);
    public static final String LISTENER_ADDRESS = "localhost";
    public static final int LISTENER_PORT = 8070;

    private final static MockHttpServer webServer = new MockHttpServer(LISTENER_ADDRESS, 8070);

    @BeforeClass
    public static void startWebServer() throws Exception {
        webServer.start();
    }

    @AfterClass
    public static void stopWebServer() {
        webServer.stop();
    }

    @Before
    public void cleanRequests() {

        webServer.cleanRequests();
    }

    private Logger createLogger(String token, String type, String loggerName, @Nullable Integer drainTimeout, @Nullable Integer fsPercentThreshold, @Nullable String bufferDir) {

        ch.qos.logback.classic.Logger logbackLogger =  (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(loggerName);

        Context logbackContext = logbackLogger.getLoggerContext();
        LogzioLogbackAppender logzioLogbackAppender = new LogzioLogbackAppender();
        logzioLogbackAppender.setContext(logbackContext);
        logzioLogbackAppender.setToken(token);
        logzioLogbackAppender.setLogzioType(type);
        logzioLogbackAppender.setLogzioUrl("http://" + LISTENER_ADDRESS + ":" + LISTENER_PORT);

        if (drainTimeout != null) {
            logzioLogbackAppender.setDrainTimeout(drainTimeout);
        }
        if (fsPercentThreshold != null) {
            logzioLogbackAppender.setFsPercentThreshold(fsPercentThreshold);
        }
        if (bufferDir != null) {
            logzioLogbackAppender.setBufferDir(bufferDir);
        }

        logzioLogbackAppender.start();

        logbackLogger.addAppender(logzioLogbackAppender);
        logbackLogger.setAdditive(true);

        return logbackLogger;
    }



    @Test
    public void simpleAppending() throws Exception {

        String token = "itMoHVaqzDNfWPniGyZxACgCesUiCyZy";
        String type = "awesomeType";
        String logger = "unitTestLogger";
        int drainTimeout = 1;

        String message1 = "Testing..";
        String message2 = "Warning test..";

        Logger testLogger = createLogger(token, type, logger, drainTimeout, null, null);

        testLogger.info(message1);
        testLogger.warn(message2);

        // Sleep double time the drain timeout
        Thread.sleep(drainTimeout * 1000 * 2);

        assertTrue(webServer.checkForLogExistance(token, type, logger, Level.INFO, message1));
        assertTrue(webServer.checkForLogExistance(token, type, logger, Level.WARN, message2));

        assertFalse(webServer.checkForLogExistance(token, type, logger, Level.INFO, message2));
        assertFalse(webServer.checkForLogExistance(token, type, logger, Level.WARN, message1));

    }
}