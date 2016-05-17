package io.logz.src;


import ch.qos.logback.classic.Level;
import ch.qos.logback.core.Context;
import com.sun.istack.internal.Nullable;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * Created by roiravhon on 5/15/16.
 */
public class LogzioSenderTest {

    private final static Logger logger = LoggerFactory.getLogger(LogzioSenderTest.class);
    public static final String LISTENER_ADDRESS = "localhost";
    public static final int LISTENER_PORT = 8070;

    private final static MockLogzioBulkListener mockListener = new MockLogzioBulkListener(LISTENER_ADDRESS, 8070);

    @BeforeClass
    public static void startMockListener() throws Exception {

        logger.info("Starting Mock listener");
        mockListener.start();
    }

    @AfterClass
    public static void stopMockListener() {

        logger.info("Stopping Mock listener");
        mockListener.stop();
    }

    @Before
    public void cleanLogs() {

        logger.info("Clearing all requests from mock listener");
        mockListener.cleanRequests();
    }

    private Logger createLogger(String token, String type, String loggerName, @Nullable Integer drainTimeout, @Nullable Integer fsPercentThreshold, @Nullable String bufferDir) {

        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}, fsPercentThreshold={}, bufferDir={}", loggerName, token, type, drainTimeout, fsPercentThreshold, bufferDir);

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

    private void recursiveDeleteDir(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File currFile : files) {
                recursiveDeleteDir(currFile);
            }
        }
        file.delete();
    }

    @Test
    public void simpleAppending() throws Exception {

        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = "awesomeType";
        String loggerName = "simpleAppending";
        int drainTimeout = 1;

        String message1 = "Testing..";
        String message2 = "Warning test..";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null);

        testLogger.info(message1);
        testLogger.warn(message2);

        // Sleep double time the drain timeout
        Thread.sleep(drainTimeout * 1000 * 2);

        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message1));
        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.WARN, message2));

        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message2));
        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.WARN, message1));

    }

    @Test
    public void multipleBufferDrains() throws Exception{

        String token = "tokenWohooToken";
        String type = "typoosh";
        String loggerName = "multipleBufferDrains";
        int drainTimeout = 2;

        String message1 = "Testing first drain";
        String message2 = "And the second drain";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null);

        testLogger.info(message1);

        // Sleep double time the drain timeout
        Thread.sleep(drainTimeout * 1000 * 2);

        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message1));
        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.WARN, message2));

        testLogger.warn(message2);

        Thread.sleep(drainTimeout * 1000 * 2);
        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.WARN, message2));
    }

    @Test
    public void longDrainTimeout() throws Exception{
        String token = "soTestingIsSuperImportant";
        String type = "andItsImportantToChangeStuff";
        String loggerName = "longDrainTimeout";
        int drainTimeout = 10;

        String message1 = "Sending one log";
        String message2 = "And one more important one";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null);

        testLogger.info(message1);
        testLogger.error(message2);

        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message1));
        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.ERROR, message2));

        // Sleep the drain timeout + 1 second
        Thread.sleep(drainTimeout * 1000 + 1000);

        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message1));
        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.ERROR, message2));
    }

    @Test
    public void changeBufferLocation() {

        String token = "nowWeWantToChangeTheBufferLocation";
        String type = "justTestingExistence";
        String loggerName = "changeBufferLocation";
        int drainTimeout = 10;
        String bufferDir = "./MyAwesomeBuffer";

        String message1 = "Just sending something";

        File buffer = new File(bufferDir);

        try {
            assertFalse(buffer.exists());

            Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, bufferDir);
            testLogger.info(message1);

            assertTrue(buffer.exists());
        }
        finally {

            try {
                recursiveDeleteDir(buffer);
            }
            catch (Exception e ) {
                logger.error("Could not delete buffer dir.. {}", e);
            }
        }
    }

    @Test
    public void fsPercentDrop() throws Exception{

        String token = "droppingLogsDueToFSOveruse";
        String type = "droppedType";
        String loggerName = "fsPercentDrop";
        int drainTimeout = 1;
        int fsPercentDrop = 1; // Should drop all logs

        String message1 = "First log that will be dropped";
        String message2 = "And a second drop";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, fsPercentDrop, null);

        testLogger.info(message1);
        testLogger.warn(message2);

        // Sleep double time the drain timeout
        Thread.sleep(drainTimeout * 1000 * 2);

        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message1));
        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.WARN, message2));
    }

    @Test
    public void serverCrash() throws Exception{

        String token = "nowWeWillCrashTheServerAndRecover";
        String type = "crashingType";
        String loggerName = "serverCrash";
        int drainTimeout = 1;

        String message1 = "Log before drop";
        String message2 = "Log during drop";
        String message3 = "Log after drop";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null);

        testLogger.info(message1);

        // Sleep double time the drain timeout
        Thread.sleep(drainTimeout * 1000 * 2);

        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message1));
        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.ERROR, message2));
        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.WARN, message3));

        mockListener.stop();
        Thread.sleep(2000);  // Just to make sure the listener is completely down

        testLogger.error(message2);
        Thread.sleep(drainTimeout * 1000 * 2);
        assertFalse(mockListener.checkForLogExistence(token, type, loggerName, Level.ERROR, message2));

        mockListener.start();

        testLogger.warn(message3);

        Thread.sleep(drainTimeout * 1000 * 2);

        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.ERROR, message2));
        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.WARN, message3));
    }

}