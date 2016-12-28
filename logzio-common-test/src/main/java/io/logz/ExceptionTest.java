package io.logz;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by MarinaRazumovsky on 27/12/2016.
 */
public abstract class ExceptionTest extends BaseTest {

    @Test
    public void checkExactStackTrace() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final LogbackMyRunner.ExceptionGenerator exceptionGenerator = new LogbackMyRunner.ExceptionGenerator();

        // We need to generate an exception with constant stack trace
        new Thread(new LogbackMyRunner(countDownLatch, exceptionGenerator)).start();
        countDownLatch.await();

        String token = "exceptionToken";
        String type = "stacktraceType";
        String loggerName = "traceLogger";
        int drainTimeout = 1;

        String message1 = "Any line change here can cause the test to break";

        String expectedException = "java.lang.RuntimeException: Got NPE!\n" +
                "\tat io.logz.LogbackMyRunner$ExceptionGenerator.generateNPE(LogbackMyRunner.java:33)\n" +
                "\tat io.logz.LogbackMyRunner.run(LogbackMyRunner.java:18)\n" +
                "\tat java.lang.Thread.run(Thread.java:745)\n" +
                "Caused by: java.lang.NullPointerException: null\n" +
                "\tat io.logz.LogbackMyRunner$ExceptionGenerator.generateNPE(LogbackMyRunner.java:31)\n" +
                "\t... 2 common frames omitted\n";

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1, exceptionGenerator.getE());

        sleepSeconds(drainTimeout * 2);

        assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, level1);
        assertThat(logRequest.getStringFieldOrNull("exception")).isEqualTo(expectedException);
    }
}
