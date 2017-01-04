package io.logz;


import io.logz.sender.LogzioSender;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class LogzioSenderTest extends BaseSenderTest {

    @Test
    public void simpleAppending() throws Exception {

        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = "awesomeType";
        String loggerName = "simpleAppending";
        int drainTimeout = 1;

        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeout, 98, null, 10*1000);

        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));
        testSender.sendMessage(new TestLogzioMessage(message2, loggerName,System.currentTimeMillis() ));
        sleepSeconds(drainTimeout * 2);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message1, token, type, loggerName);
        assertLogReceivedIs(message2, token, type, loggerName);
    }

    @Test
    public void multipleBufferDrains() throws Exception {

        String token = "tokenWohooToken";
        String type = "typoosh";
        String loggerName = "multipleBufferDrains";
        int drainTimeoutSec = 2;

        String message1 = "Testing first drain - " + random(5);
        String message2 = "And the second drain" + random(5);

        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeoutSec, 98, null, 10 * 1000);

        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));

        sleepSeconds(2 * drainTimeoutSec);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName);

        testSender.sendMessage(new TestLogzioMessage(message2, loggerName,System.currentTimeMillis() ));

        sleepSeconds(2 * drainTimeoutSec);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message2, token, type, loggerName);
    }

    @Test
    public void longDrainTimeout() throws Exception {
        String token = "soTestingIsSuperImportant";
        String type = "andItsImportantToChangeStuff";
        String loggerName = "longDrainTimeout";
        int drainTimeout = 10;

        String message1 = "Sending one log - " + random(5);
        String message2 = "And one more important one - " + random(5);

        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeout, 98, null, 10 * 1000);


        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));
        testSender.sendMessage(new TestLogzioMessage(message2, loggerName,System.currentTimeMillis() ));

        assertNumberOfReceivedMsgs(0);

        sleepSeconds(drainTimeout + 1);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message1, token, type, loggerName);
        assertLogReceivedIs(message2, token, type, loggerName);
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

        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeout, 98, bufferDir, 10 * 1000);

        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));

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

        String message1 = "First log that will be dropped - " +  random(5);
        String message2 = "And a second drop - " + random(5);


        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeoutSec, fsPercentDrop, null, 10 * 1000);

        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));
        testSender.sendMessage(new TestLogzioMessage(message2, loggerName,System.currentTimeMillis() ));

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

        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeout, 98, null,  1000);


        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName);

        mockListener.stop();

        testSender.sendMessage(new TestLogzioMessage(message2, loggerName,System.currentTimeMillis() ));
        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1); // haven't changed - still 1

        mockListener.start();

        testSender.sendMessage(new TestLogzioMessage(message3, loggerName,System.currentTimeMillis() ));

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(3);
        assertLogReceivedIs(message2, token, type, loggerName);
        assertLogReceivedIs(message3, token, type, loggerName);
    }

    @Test
    public void getTimeoutFromServer() throws Exception {
        String token = "gettingTimeoutFromServer";
        String type = "timeoutType";
        String loggerName = "getTimeoutFromServer";
        int drainTimeout = 1;
        int serverTimeout = 2000;

        String message1 = "Log that will be sent - " +  random(5);
        String message2 = "Log that would timeout and then being re-sent - " + random(5);

        int socketTimeout = serverTimeout / 2;
        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeout, 98, null,  socketTimeout);


        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName);

        mockListener.setTimeoutMillis(serverTimeout);
        mockListener.setMakeServerTimeout(true);

        testSender.sendMessage(new TestLogzioMessage(message2, loggerName,System.currentTimeMillis() ));

        sleepSeconds((socketTimeout / 1000) * LogzioSender.MAX_RETRIES_ATTEMPTS + retryTotalDelay());

        assertNumberOfReceivedMsgs(1); // Stays the same

        mockListener.setMakeServerTimeout(false);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(2);

        assertLogReceivedIs(message2, token, type, loggerName);
    }

    private int retryTotalDelay() {
        int sleepBetweenRetry = LogzioSender.INITIAL_WAIT_BEFORE_RETRY_MS / 1000;
        int totalSleepTime = 0;
        for (int i = 1; i < LogzioSender.MAX_RETRIES_ATTEMPTS; i++) {
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

        TestLogzioSender testSender = createLogzioSender(token, type, loggerName, drainTimeout, 98, null,  10*1000);

        testSender.sendMessage(new TestLogzioMessage(message1, loggerName,System.currentTimeMillis() ));

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName);

        mockListener.setFailWithServerError(true);

        testSender.sendMessage(new TestLogzioMessage(message2, loggerName,System.currentTimeMillis() ));

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1); // Haven't changed

        mockListener.setFailWithServerError(false);

        Thread.sleep(drainTimeout * 1000 * 2);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message2, token, type, loggerName);
    }


}