package io.logz;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.Context;
import io.logz.logback.LogzioLogbackAppender;
import io.logz.test.MockLogzioBulkListener;
import io.logz.test.TestEnvironment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by roiravhon on 9/11/16.
 */
public class ExceptionTest {

    private final static Logger logger = LoggerFactory.getLogger(LogzioLogbackAppenderTest.class);
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

    @Test
    public void checkExactStackTrace() throws Exception {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final MyRunner.ExceptionGenerator exceptionGenerator = new MyRunner.ExceptionGenerator();

        // We need to generate an exception with constant stack trace
        new Thread(new MyRunner(countDownLatch, exceptionGenerator)).start();
        countDownLatch.await();

        String token = "exceptionToken";
        String type = "stacktraceType";
        String loggerName = "traceLogger";
        int drainTimeout = 1;

        String message1 = "Any line change here can cause the test to break";

        String expectedException = "java.lang.RuntimeException: Got NPE!\n" +
                "\tat io.logz.MyRunner$ExceptionGenerator.generateNPE(MyRunner.java:33)\n" +
                "\tat io.logz.MyRunner.run(MyRunner.java:18)\n" +
                "\tat java.lang.Thread.run(Thread.java:745)\n" +
                "Caused by: java.lang.NullPointerException: null\n" +
                "\tat io.logz.MyRunner$ExceptionGenerator.generateNPE(MyRunner.java:31)\n" +
                "\t... 2 common frames omitted\n";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout);

        testLogger.info(message1, exceptionGenerator.getE());

        logger.info("Sleeping {} [sec]...", drainTimeout * 2);
        try {
            Thread.sleep(drainTimeout * 2 * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertThat(logRequest.getStringFieldOrNull("exception")).isEqualTo(expectedException);
    }

    protected Logger createLogger(String token, String type, String loggerName, Integer drainTimeout) {

        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}",
                loggerName, token, type, drainTimeout);

        ch.qos.logback.classic.Logger logbackLogger =  (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);

        Context logbackContext = logbackLogger.getLoggerContext();
        LogzioLogbackAppender logzioLogbackAppender = new LogzioLogbackAppender();
        logzioLogbackAppender.setContext(logbackContext);
        logzioLogbackAppender.setToken(token);
        logzioLogbackAppender.setLogzioType(type);
        logzioLogbackAppender.setDebug(true);
        logzioLogbackAppender.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioLogbackAppender.setAddHostname(false);

        if (drainTimeout != null) {
            logzioLogbackAppender.setDrainTimeoutSec(drainTimeout);
        }

        File tempDir = TestEnvironment.createTempDirectory();
        tempDir.deleteOnExit();
        logzioLogbackAppender.setBufferDir(tempDir.getAbsolutePath());

        logzioLogbackAppender.start();
        assertThat(logzioLogbackAppender.isStarted()).isTrue();
        logbackLogger.addAppender(logzioLogbackAppender);
        logbackLogger.setAdditive(false);

        return logbackLogger;
    }
}
