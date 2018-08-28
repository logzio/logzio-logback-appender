package io.logz.logback;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.logz.sender.com.google.gson.Gson;
import io.logz.test.MockLogzioBulkListener;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class LogzioLogbackAppenderTest extends BaseLogbackAppenderTest {

    private final static Logger logger = LoggerFactory.getLogger(LogzioLogbackAppenderTest.class);

    @Test
    public void validateJsonMessage(){
        String token = "validatingAdditionalFields";
        String type = "willTryWithOrWithoutEnvironmentVariables";
        String loggerName = "additionalLogger";
        int drainTimeout = 1;
        String messageText = "message test";

        Map<String, Object> map = new HashMap<>();
        map.put("message", messageText);
        map.put("userName", "test");
        map.put("email", "test@email.com");

        String message1 = new Gson().toJson(map);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);
        LogzioLogbackAppender logzioLogbackAppender =
                (LogzioLogbackAppender)((ch.qos.logback.classic.Logger)testLogger).getAppender("LogzioLogbackAppender");
        logzioLogbackAppender.setFormat("json");
        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(messageText);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

        assertThat(logRequest.getStringFieldOrNull("userName")).isNotNull().isEqualTo("test");
        assertThat(logRequest.getStringFieldOrNull("email")).isNotNull().isEqualTo("test@email.com");
    }

    @Test
    public void simpleAppending() {
        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = "awesomeType";
        String loggerName = "simpleAppending";
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);

        mockListener.assertNumberOfReceivedMsgs(2);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.levelStr);
        mockListener.assertLogReceivedIs(message2, token, type, loggerName, Level.WARN.levelStr);
    }

    @Test
    public void simpleGzipAppending() {
        String token = "aBcDeFgHiJkLmNoPqRsTGzIp";
        String type = "awesomeGzipType";
        String loggerName = "simpleGzipAppending";
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, true);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);

        mockListener.assertNumberOfReceivedMsgs(2);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.levelStr);
        mockListener.assertLogReceivedIs(message2, token, type, loggerName, Level.WARN.levelStr);
    }
    
    @Test
    public void validateAdditionalFields() {
        String token = "validatingAdditionalFields";
        String type = "willTryWithOrWithoutEnvironmentVariables";
        String loggerName = "additionalLogger";
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);
        Map<String,String > additionalFields = new HashMap<>();
        String additionalFieldsString = "java_home=$JAVA_HOME;testing=yes;message=override";
        additionalFields.put("java_home", System.getenv("JAVA_HOME"));
        additionalFields.put("testing", "yes");

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, additionalFieldsString);
        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertAdditionalFields(logRequest, additionalFields);
    }

    @Test
    public void existingHostname() throws Exception {
        String token = "checkingHostname";
        String type = "withOrWithoutHostnamr";
        String loggerName = "runningOutOfIdeasHere";
        int drainTimeout = 1;
        String message1 = "Hostname log - " +  random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, true, false, null);
        testLogger.info(message1);

        // Sleep double time the drain timeout
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

        String hostname = InetAddress.getLocalHost().getHostName();
        assertThat(logRequest.getHost()).isEqualTo(hostname);
    }

    @Test
    public void existingLine() {
        String token = "checkingLine";
        String type = "withLineType";
        String loggerName = "test";
        int drainTimeout = 1;
        String message1 = "Hostname log - " +  random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, true, null);
        testLogger.info(message1);

        // Sleep double time the drain timeout
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertThat(logRequest.getStringFieldOrNull("line")).isNotNull();
    }


    @SuppressWarnings("ConstantConditions")
    @Test
    public void sendException() {
        String token = "checkingExceptions";
        String type = "badType";
        String loggerName = "exceptionProducer";
        int drainTimeout = 1;
        Throwable exception = null;
        String message1 = "This is not an int..";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);

        try {
            Integer.parseInt(message1);
        } catch (Exception e) {
            exception = e;
            testLogger.info(message1, e);
        }
        assertThat(exception).isNotNull();

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

        String exceptionField = logRequest.getStringFieldOrNull("exception");
        if (exceptionField == null) fail("Exception field does not exists");

        assertThat(exceptionField.replace("\\", "")).contains(exception.getMessage());
    }

    @Test
    public void testMDC() {
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

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);
        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertThat(logRequest.getStringFieldOrNull(mdcKey)).isEqualTo(mdcValue);
    }

    @Test
    public void testMarker() {
        String token = "markerToken";
        String type = "markerType";
        String loggerName = "markerTesting";
        String markerKey = "marker";
        String markerTestValue = "MyMarker";
        int drainTimeout = 1;
        String message1 = "Simple log line - "+random(5);
        Marker marker = MarkerFactory.getMarker(markerTestValue);
        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);
//        Map logstashMarker = new HashMap();
//        logstashMarker.put("customkey_str", "value1");
//        logstashMarker.put("projectid_int", 5);
//        testLogger.info(Markers.appendEntries(logstashMarker) , message1);

        testLogger.info(marker, message1);
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertThat(logRequest.getStringFieldOrNull(markerKey)).isEqualTo(markerTestValue);
    }

    @Test
    public void testContextReset() {
        logger.info("context.reset() is called when logback loads a new logback.xml in-flight");
        String token = "testingContextReset";
        String type = "contextResetType";
        String loggerName = "ContextResetLogger";
        int drainTimeout = 1;

        String message1 = "Before Reset Line - "+random(5);
        String message2 = "After Reset Line - "+random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);

        testLogger.info(message1);
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // This clears everything from the context
        loggerContext.reset();

        // We need to add the appender again
        testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null);

        testLogger.warn(message2);
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(2);
        logRequest = mockListener.assertLogReceivedByMessage(message2);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.WARN.levelStr);
    }

    @Test
    public void testTokenAndLogzioUrlFromSystemEnvironment() {
        String token = System.getenv("JAVA_HOME");
        String type = "testType";
        String loggerName = "testLogger";
        int drainTimeout = 1;

        String message1 = "Just a log - " + random(5);
        Logger testLogger = createLogger("$JAVA_HOME", type, loggerName, drainTimeout, false, false, null);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
    }

    private void assertAdditionalFields(MockLogzioBulkListener.LogRequest logRequest, Map<String, String> additionalFields) {
        additionalFields.forEach((field, value) -> {
            String fieldValueInLog = logRequest.getStringFieldOrNull(field);
            assertThat(fieldValueInLog)
                    .describedAs("Field '{}' in Log [{}]", field, logRequest.getJsonObject().toString())
                    .isNotNull()
                    .isEqualTo(value);
        });
    }

}