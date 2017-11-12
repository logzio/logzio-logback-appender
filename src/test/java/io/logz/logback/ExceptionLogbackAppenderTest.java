package io.logz.logback;

import ch.qos.logback.classic.Level;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

import static io.logz.test.MockLogzioBulkListener.LogRequest;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by roiravhon on 9/11/16.
 */
public class ExceptionLogbackAppenderTest extends BaseLogbackAppenderTest {

    private final static Logger logger = LoggerFactory.getLogger(LogzioLogbackAppenderTest.class);

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
                "\tat io.logz.logback.MyRunner$ExceptionGenerator.generateNPE(MyRunner.java:33)\n" +
                "\tat io.logz.logback.MyRunner.run(MyRunner.java:18)\n" +
                "\tat java.lang.Thread.run(Thread.java:748)\n" +
                "Caused by: java.lang.NullPointerException: null\n" +
                "\tat io.logz.logback.MyRunner$ExceptionGenerator.generateNPE(MyRunner.java:31)\n" +
                "\t... 2 common frames omitted\n";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);

        testLogger.info(message1, exceptionGenerator.getE());
        sleepSeconds(drainTimeout * 2);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertThat(logRequest.getStringFieldOrNull("exception")).isEqualTo(expectedException);
    }
}
