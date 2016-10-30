package io.logz;


import ch.qos.logback.classic.Level;
import io.logz.MockLogzioBulkListener.LogRequest;
import io.logz.logback.LogzioSender;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class LogzioSenderTest extends BaseTest {

    @Test
    public void simpleAppending() throws Exception {

        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = "awesomeType";
        String loggerName = "simpleAppending";
        int drainTimeout = 1;

        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, null);

        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message1, token, type, loggerName, Level.INFO);
        assertLogReceivedIs(message2, token, type, loggerName, Level.WARN);
    }

    @Test
    public void multipleBufferDrains() throws Exception {

        String token = "tokenWohooToken";
        String type = "typoosh";
        String loggerName = "multipleBufferDrains";
        int drainTimeoutSec = 2;

        String message1 = "Testing first drain - " + random(5);
        String message2 = "And the second drain" + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeoutSec, null, null, null, false, null);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeoutSec);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, Level.INFO);

        testLogger.warn(message2);

        sleepSeconds(2 * drainTimeoutSec);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message2, token, type, loggerName, Level.WARN);
    }

    @Test
    public void longDrainTimeout() throws Exception {
        String token = "soTestingIsSuperImportant";
        String type = "andItsImportantToChangeStuff";
        String loggerName = "longDrainTimeout";
        int drainTimeout = 10;

        String message1 = "Sending one log - " + random(5);
        String message2 = "And one more important one - " + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, null);

        testLogger.info(message1);
        testLogger.error(message2);

        assertNumberOfReceivedMsgs(0);

        sleepSeconds(drainTimeout + 1);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message1, token, type, loggerName, Level.INFO);
        assertLogReceivedIs(message2, token, type, loggerName, Level.ERROR);
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

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, bufferDir, null, false, null);
        testLogger.info(message1);

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

        Logger testLogger = createLogger(token, type, loggerName, drainTimeoutSec, fsPercentDrop, null, null, false, null);

        testLogger.info(message1);
        testLogger.warn(message2);

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

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, 1000, false, null);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, Level.INFO);

        mockListener.stop();

        testLogger.error(message2);
        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1); // haven't changed - still 1

        mockListener.start();

        testLogger.warn(message3);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(3);
        assertLogReceivedIs(message2, token, type, loggerName, Level.ERROR);
        assertLogReceivedIs(message3, token, type, loggerName, Level.WARN);
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
        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, socketTimeout, false, null);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, Level.INFO);

        mockListener.setTimeoutMillis(serverTimeout);
        mockListener.setMakeServerTimeout(true);

        testLogger.warn(message2);

        sleepSeconds((socketTimeout / 1000) * LogzioSender.MAX_RETRIES_ATTEMPTS + retryTotalDelay());

        assertNumberOfReceivedMsgs(1); // Stays the same

        mockListener.setMakeServerTimeout(false);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(2);

        assertLogReceivedIs(message2, token, type, loggerName, Level.WARN);
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

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, null);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        assertLogReceivedIs(message1, token, type, loggerName, Level.INFO);

        mockListener.setFailWithServerError(true);

        testLogger.warn(message2);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1); // Haven't changed

        mockListener.setFailWithServerError(false);

        Thread.sleep(drainTimeout * 1000 * 2);

        assertNumberOfReceivedMsgs(2);
        assertLogReceivedIs(message2, token, type, loggerName, Level.WARN);
    }

    @Test
    public void validateAdditionalFields() throws Exception {
        String token = "validatingAdditionalFields";
        String type = "willTryWithOrWithoutEnvironmentVariables";
        String loggerName = "additionalLogger";
        int drainTimeout = 1;

        String message1 = "Just a log - " + random(5);
        Map<String,String > additionalFields = new HashMap<>();

        String additionalFieldsString = "java_home=$JAVA_HOME;testing=yes;message=override";
        additionalFields.put("java_home", System.getenv("JAVA_HOME"));
        additionalFields.put("testing", "yes");

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, additionalFieldsString);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO);
        assertAdditionalFields(logRequest, additionalFields);
    }

    @Test
    public void existingHostname() throws Exception {
        String token = "checkingHostname";
        String type = "withOrWithoutHostnamr";
        String loggerName = "runningOutOfIdeasHere";
        int drainTimeout = 1;

        String message1 = "Hostname log - " +  random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, true, null);

        testLogger.info(message1);

        // Sleep double time the drain timeout
        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO);

        String hostname = InetAddress.getLocalHost().getHostName();
        assertThat(logRequest.getHost()).isEqualTo(hostname);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void sendException() throws Exception {
        String token = "checkingExceptions";
        String type = "badType";
        String loggerName = "exceptionProducer";
        int drainTimeout = 1;
        Throwable exception = null;

        String message1 = "This is not an int..";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, null);

        try {
            Integer.parseInt(message1);
        } catch (Exception e) {
            exception = e;
            testLogger.info(message1, e);
        }
        assertThat(exception).isNotNull();

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO);

        String exceptionField = logRequest.getStringFieldOrNull("exception");
        if (exceptionField == null) fail("Exception field does not exists");

        assertThat(exceptionField.replace("\\", "")).contains(exception.getMessage());
    }

    @Test
    public void testMDC() throws Exception {
        String token = "mdcTokensAreTheBest";
        String type = "mdcType";
        String loggerName = "mdcTesting";
        int drainTimeout = 1;

        String message1 = "Simple log line - "+random(5);

        String mdcKey = "mdc-key";
        String mdcValue = "mdc-value";

        Map<String, String> mdcKv = new HashMap<>();
        mdcKv.put(mdcKey, mdcValue);
        mdcKv.put("logger", "Doesn't matter");

        MDC.put(mdcKey, mdcValue);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, null, null, null, false, null);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO);

        assertThat(logRequest.getStringFieldOrNull(mdcKey)).isEqualTo(mdcValue);
    }
}