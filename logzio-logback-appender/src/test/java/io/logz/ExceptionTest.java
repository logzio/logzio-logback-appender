package io.logz;

import ch.qos.logback.classic.Level;
import io.logz.MockLogzioBulkListener.LogRequest;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by roiravhon on 9/11/16.
 */
public class ExceptionTest extends BaseTest {

    @Test
    public void checkExactStackTrace() throws InterruptedException {
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

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, null);

        testLogger.info(message1, exceptionGenerator.getE());

        sleepSeconds(drainTimeout * 2);

        assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO);
        assertThat(logRequest.getStringFieldOrNull("exception")).isEqualTo(expectedException);
    }
}
