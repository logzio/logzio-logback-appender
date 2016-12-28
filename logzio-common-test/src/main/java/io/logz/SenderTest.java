package io.logz;

import org.junit.Test;

import java.io.File;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by MarinaRazumovsky on 27/12/2016.
 */
public abstract class SenderTest extends BaseTest {



    // copied from LogzioSender TODO: ???
     private static final int MAX_SIZE_IN_BYTES = 3 * 1024 * 1024;  // 3 MB
    public static final int INITIAL_WAIT_BEFORE_RETRY_MS = 2000;
    public static final int MAX_RETRIES_ATTEMPTS = 3;


    @Test
    public void simpleAppending() throws Exception {

        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = "awesomeType2";
        String loggerName = "simpleAppending";
        int drainTimeout = 2;

        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        TestSenderWrapper testSender = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10 * 1000,false, "",30 , port);

        Optional level1 = testSender.info(loggerName, message1);
        Optional level2 = testSender.warn(loggerName, message2);
        sleepSeconds(drainTimeout  *3);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message1, token, type, loggerName, level1);
        assertLogReceivedIs(message2, token, type, loggerName, level2);
    }

    @Test
    public void multipleBufferDrains() throws Exception {

        String token = "tokenWohooToken";
        String type = "typoosh";
        String loggerName = "multipleBufferDrains";
        int drainTimeoutSec = 2;

        String message1 = "Testing first drain - " + random(5);
        String message2 = "And the second drain" + random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeoutSec, 98,
                null, 10 * 1000,false, "",30 , port);


        Optional level1 = testWrapper.info(loggerName, message1);

        sleepSeconds(2 * drainTimeoutSec);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, level1);

        Optional level2 = testWrapper.warn(loggerName, message2);

        sleepSeconds(2 * drainTimeoutSec);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message2, token, type, loggerName, level2);
    }

    @Test
    public void longDrainTimeout() throws Exception {
        String token = "soTestingIsSuperImportant";
        String type = "andItsImportantToChangeStuff";
        String loggerName = "longDrainTimeout";
        int drainTimeout = 10;

        String message1 = "Sending one log - " + random(5);
        String message2 = "And one more important one - " + random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10 * 1000,false, "",30 , port);


        Optional level1 = testWrapper.info(loggerName, message1);
        Optional level2 = testWrapper.error(loggerName, message2);

        assertNumberOfReceivedMsgs(0);

        sleepSeconds(drainTimeout + 1);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message1, token, type, loggerName, level1);
        assertLogReceivedIs(message2, token, type, loggerName, level2);
    }

    @Test
    public void testLoggerCreatesDirectoryWhichDoesNotExists() {
        String token = "nowWeWantToChangeTheBufferLocation";
        String type = "justTestingExistence";
        String loggerName = "changeBufferLocation";
        int drainTimeout = 10;
        File tempDirectory = TestEnvironment.createTempDirectory();
        String bufferDir = new File(tempDirectory, "dirWhichDoesNotExists").getAbsolutePath();

        String message1 = "Just sending something - " + random(5);

        File buffer = new File(bufferDir);

        assertFalse(buffer.exists());

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                bufferDir, 10 * 1000,false, "",30 , port);

        testWrapper.info(loggerName, message1);

        assertTrue(buffer.exists());
    }

    @Test
    public void fsPercentDrop() throws Exception {
        String token = "droppingLogsDueToFSOveruse";
        String type = "droppedType";
        String loggerName = "fsPercentDrop";
        int drainTimeoutSec = 1;

        File tempDirectoryThatWillBeInTheSameFsAsTheBuffer = TestEnvironment.createTempDirectory();
        tempDirectoryThatWillBeInTheSameFsAsTheBuffer.deleteOnExit();

        int fsPercentDrop = 100 - ((int) (((double) tempDirectoryThatWillBeInTheSameFsAsTheBuffer.getUsableSpace() /
                tempDirectoryThatWillBeInTheSameFsAsTheBuffer.getTotalSpace()) * 100)) - 1;

        String message1 = "First log that will be dropped - " + random(5);
        String message2 = "And a second drop - " + random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeoutSec, fsPercentDrop,
                null, 10 * 1000,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1);
        Optional level2 = testWrapper.warn(loggerName, message2);

        sleepSeconds(2 * drainTimeoutSec);

        assertNumberOfReceivedMsgs(0);
    }

    @Test
    public void serverCrash() throws Exception {
        String token = "nowWeWillCrashTheServerAndRecover";
        String type = "crashingType";
        String loggerName = "serverCrash";
        int drainTimeout = 1;

        String message1 = "Log before drop - " + random(5);
        String message2 = "Log during drop - " + random(5);
        String message3 = "Log after drop - " + random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 1000,false, "",30 , port);


        Optional level1 = testWrapper.info(loggerName, message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, level1);

        mockListener.stop();

        Optional level2 = testWrapper.error(loggerName, message2);
        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1); // haven't changed - still 1

        mockListener.start();

        Optional level3 = testWrapper.warn(loggerName, message3);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(3);
        assertLogReceivedIs(message2, token, type, loggerName, level2);
        assertLogReceivedIs(message3, token, type, loggerName, level3);
    }

    @Test
    public void getTimeoutFromServer() throws Exception {
        String token = "gettingTimeoutFromServer";
        String type = "timeoutType";
        String loggerName = "getTimeoutFromServer";
        int drainTimeout = 1;
        int serverTimeout = 2000;

        String message1 = "Log that will be sent - " + random(5);
        String message2 = "Log that would timeout and then being re-sent - " + random(5);

        int socketTimeout = serverTimeout / 2;

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, socketTimeout,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, level1);

        mockListener.setTimeoutMillis(serverTimeout);
        mockListener.setMakeServerTimeout(true);

        Optional level2 = testWrapper.warn(loggerName, message2);

        sleepSeconds((socketTimeout / 1000) * MAX_RETRIES_ATTEMPTS + retryTotalDelay());

        assertNumberOfReceivedMsgs(1); // Stays the same

        mockListener.setMakeServerTimeout(false);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(2);

        assertLogReceivedIs(message2, token, type, loggerName, level2);
    }

    private int retryTotalDelay() {
        int sleepBetweenRetry = INITIAL_WAIT_BEFORE_RETRY_MS / 1000;
        int totalSleepTime = 0;
        for (int i = 1; i < MAX_RETRIES_ATTEMPTS; i++) {
            totalSleepTime += sleepBetweenRetry;
            sleepBetweenRetry *= 2;
        }
        return totalSleepTime;

    }

    @Test
    public void getExceptionFromServer() throws Exception {
        String token = "gettingExceptionFromServer";
        String type = "exceptionType";
        String loggerName = "getExceptionFromServer";
        int drainTimeout = 1;

        String message1 = "Log that will be sent - " +  random(5);
        String message2 = "Log that would get exception and be sent again - " + random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1);


        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, level1);

        mockListener.setFailWithServerError(true);

        Optional level2 = testWrapper.warn(loggerName, message2);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1); // Haven't changed

        mockListener.setFailWithServerError(false);

        Thread.sleep(drainTimeout * 1000 * 2);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message2, token, type, loggerName, level2);
    }

}
