package io.logz;


public class SimpleSenderTest extends SenderTest {

    @Override
    TestSenderWrapper getTestSenderWrapper(String token, String type, String loggerName, Integer drainTimeout, Integer fsPercentThreshold, String bufferDir, Integer socketTimeout, boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds,int port) {
        return new SimpleTestSenderWrapper(token,type,loggerName,drainTimeout,fsPercentThreshold,bufferDir,socketTimeout,port);

    }
}