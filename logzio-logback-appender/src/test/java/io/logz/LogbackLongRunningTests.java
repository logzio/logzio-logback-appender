package io.logz;

public class LogbackLongRunningTests extends LongRunningTests {


    @Override
    TestSenderWrapper getTestSenderWrapper(String token, String type, String loggerName, Integer drainTimeout, Integer fsPercentThreshold, String bufferDir, Integer socketTimeout, boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds, int port) {
        return new LogbackAppenderWrapper(token,type,loggerName,drainTimeout,fsPercentThreshold,bufferDir,socketTimeout,addHostname, additionalFields, gcPersistedQueueFilesIntervalSeconds,  port);
    }
}
