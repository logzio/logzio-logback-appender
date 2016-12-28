package io.logz;


import ch.qos.logback.classic.LoggerContext;
import io.logz.MockLogzioBulkListener.LogRequest;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.Optional;


public class LogbackLogzioSenderTest extends AppenderTest {

    //context.reset() is called when logback loads a new logback.xml in-flight
    @Test
    public void testContextReset() throws Exception {

        String token = "testingContextReset";
        String type = "contextResetType";
        String loggerName = "ContextResetLogger";
        int drainTimeout = 1;

        String message1 = "Before Reset Line - "+random(5);
        String message2 = "After Reset Line - "+random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1);
        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, level1);

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // This clears everything from the context
        loggerContext.reset();

        // We need to add the appender again
        testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level2 = testWrapper.warn(loggerName, message2);
        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(2);
        logRequest = assertLogReceivedByMessage(message2);
        assertLogReceivedIs(logRequest, token, type, loggerName, level2);
    }

    @Test
    public void testTokenAndLogzioUrlFromSystemEnvironment() {
        String token = System.getenv("JAVA_HOME");
        String type = "testType";
        String loggerName = "testLogger";
        int drainTimeout = 1;

        String message1 = "Just a log - " + random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper("$JAVA_HOME", type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, level1);
    }

    @Override
    TestSenderWrapper getTestSenderWrapper(String token, String type, String loggerName, Integer drainTimeout, Integer fsPercentThreshold, String bufferDir, Integer socketTimeout, boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds, int port) {
        return new LogbackAppenderWrapper(token,type,loggerName,drainTimeout,fsPercentThreshold,bufferDir,socketTimeout,addHostname, additionalFields, gcPersistedQueueFilesIntervalSeconds,  port);
    }
}