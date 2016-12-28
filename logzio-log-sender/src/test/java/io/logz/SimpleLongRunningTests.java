package io.logz;

/**
 * Created by MarinaRazumovsky on 27/12/2016.
 */
public class SimpleLongRunningTests extends LongRunningTests {

    @Override
    TestSenderWrapper getTestSenderWrapper(String token, String type, String loggerName, Integer drainTimeout, Integer fsPercentThreshold, String bufferDir, Integer socketTimeout, boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds,int port) {
        return new SimpleTestSenderWrapper(token,type,loggerName,drainTimeout,fsPercentThreshold,bufferDir,socketTimeout,port);

    }
}
