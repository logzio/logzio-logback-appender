package io.logz;

/**
 * Created by roiravhon on 9/11/16.
 */
public class LogbackExceptionTest extends ExceptionTest {

    @Override
    TestSenderWrapper getTestSenderWrapper(String token, String type, String loggerName, Integer drainTimeout, Integer fsPercentThreshold, String bufferDir, Integer socketTimeout, boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds, int port) {
        return new LogbackAppenderWrapper(token,type,loggerName,drainTimeout,fsPercentThreshold,bufferDir,socketTimeout,addHostname, additionalFields, gcPersistedQueueFilesIntervalSeconds,  port);
    }
}
