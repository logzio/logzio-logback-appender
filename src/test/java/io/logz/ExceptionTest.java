package io.logz;

import ch.qos.logback.classic.Level;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

import static io.logz.LogzioSenderTest.createLogger;
import static org.junit.Assert.assertTrue;

/**
 * Created by roiravhon on 9/11/16.
 */
public class ExceptionTest {

    // This class is separate, since we are checking stacktrace here and need everything to be exactly on the same line

    private final static Logger logger = LoggerFactory.getLogger(ExceptionTest.class);
    private static final String LISTENER_ADDRESS = "localhost";
    private final static MockLogzioBulkListener mockListener = new MockLogzioBulkListener(LISTENER_ADDRESS, 8070);

    private static class ExceptionGenerator {
        Exception e;


        public void generateNPE() {
            String v = null;
            try {

                try {
                    if (1 == 1) v.toString();
                } catch (NullPointerException e) {
                    throw new RuntimeException("Got NPE!", e);
                }
            } catch (Exception e) {
                this.e = e;
            }

        }

        public Exception getE() {
            return e;
        }
    }

    private static class MyRunner implements Runnable {
        final CountDownLatch countDownLatch;
        final ExceptionGenerator exceptionGenerator;

        public MyRunner(CountDownLatch countDownLatch, ExceptionGenerator exceptionGenerator) {
            this.countDownLatch = countDownLatch;
            this.exceptionGenerator = exceptionGenerator;
        }

        public void run() {
            exceptionGenerator.generateNPE();
            countDownLatch.countDown();
        }
    }

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

    @Test
    public void checkExactStackTrace() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final ExceptionGenerator exceptionGenerator = new ExceptionGenerator();

        // We need to generate an exception with constant stack trace
        new Thread(new MyRunner(countDownLatch, exceptionGenerator)).start();
        countDownLatch.await();

        String token = "exceptionToken";
        String type = "stacktraceType";
        String loggerName = "traceLogger";
        int drainTimeout = 1;

        String message1 = "Any line change here can cause the test to break";

        String exactException = "java.lang.RuntimeException: Got NPE!\n" +
                "\tat io.logz.ExceptionTest$ExceptionGenerator.generateNPE(ExceptionTest.java:38)\n" +
                "\tat io.logz.ExceptionTest$MyRunner.run(ExceptionTest.java:61)\n" +
                "\tat java.lang.Thread.run(Thread.java:745)\n" +
                "Caused by: java.lang.NullPointerException: null\n" +
                "\tat io.logz.ExceptionTest$ExceptionGenerator.generateNPE(ExceptionTest.java:36)\n" +
                "\t... 2 common frames omitted\n";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, null);

        testLogger.info(message1, exceptionGenerator.getE());

        // Sleep double time the drain timeout
        Thread.sleep(drainTimeout * 1000 * 2);

        // If this test breaks, that might be because the stack trace is different. Try not to move things around in here
        assertTrue(mockListener.checkForLogExistence(token, type, loggerName, Level.INFO, message1, false, null, null, null, exactException));
    }
}
