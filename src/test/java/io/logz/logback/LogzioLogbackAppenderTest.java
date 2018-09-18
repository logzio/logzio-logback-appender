package io.logz.logback;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Context;
import io.logz.sender.com.google.gson.Gson;
import io.logz.test.MockLogzioBulkListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(Parameterized.class)
public class LogzioLogbackAppenderTest extends BaseLogbackAppenderTest {

    private final static Logger logger = LoggerFactory.getLogger(LogzioLogbackAppenderTest.class);
    private LogzioLogbackAppender logzioLogbackAppender;

    @Parameterized.Parameters
    public static Collection<Object[]> logzioSenderBuilders() {
        List<LogzioLogbackAppender> appenders = new ArrayList<>();
        appenders.add(new LogzioLogbackAppender());
        LogzioLogbackAppender inMemoryQueueApeender = new LogzioLogbackAppender();
        inMemoryQueueApeender.setInMemoryQueue(true);
        appenders.add(inMemoryQueueApeender);

        Collection<Object[]> result = new ArrayList<>();
        for (LogzioLogbackAppender appender : appenders) {
            result.add(new Object[]{appender});
        }
        return result;
    }

    public LogzioLogbackAppenderTest(LogzioLogbackAppender logzioLogbackAppender) {
        this.logzioLogbackAppender = logzioLogbackAppender;
    }

    @Test
    public void validateJsonMessage(){
        String token = "validateJsonMessageToken";
        String type = "validateJsonMessageType" + random(8);
        String loggerName = "validateJsonMessageLogger" + random(8);
        int drainTimeout = 1;
        String messageText = "message test";

        Map<String, Object> map = new HashMap<>();
        map.put("message", messageText);
        map.put("userName", "test");
        map.put("email", "test@email.com");

        String message1 = new Gson().toJson(map);

        logzioLogbackAppender.setFormat("json");
        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);

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
        String type = "awesomeType" + random(8);
        String loggerName = "simpleAppending" + random(8);
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);
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
        String type = "awesomeGzipType" + random(8);
        String loggerName = "simpleGzipAppending" + random(8);
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
        String type = "willTryWithOrWithoutEnvironmentVariables" + random(8);
        String loggerName = "additionalLogger" + random(8);
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);
        Map<String,String > additionalFields = new HashMap<>();
        String additionalFieldsString = "java_home=$JAVA_HOME;testing=yes;message=override";
        additionalFields.put("java_home", System.getenv("JAVA_HOME"));
        additionalFields.put("testing", "yes");

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, additionalFieldsString, false);
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
        String type = "withOrWithoutHostnamr" + random(8);
        String loggerName = "runningOutOfIdeasHere" + random(8);
        int drainTimeout = 1;
        String message1 = "Hostname log - " + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, true, false, null, false);
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
        String type = "withLineType" + random(8);
        String loggerName = "test" + random(8);
        int drainTimeout = 1;
        String message1 = "Hostname log - " + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, true, null, false);
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
        String type = "badType" + random(8);
        String loggerName = "exceptionProducer" + random(8);
        int drainTimeout = 1;
        Throwable exception = null;
        String message1 = "This is not an int..";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);

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
        String type = "mdcType" + random(8);
        String loggerName = "mdcTesting" + random(8);
        int drainTimeout = 1;
        String message1 = "Simple log line - " + random(5);
        String mdcKey = "mdc-key";
        String mdcValue = "mdc-value";

        Map<String, String> mdcKv = new HashMap<>();
        mdcKv.put(mdcKey, mdcValue);
        mdcKv.put("logger", "Doesn't matter");
        MDC.put(mdcKey, mdcValue);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);
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
        String type = "markerType" + random(8);
        String loggerName = "markerTesting" + random(8);
        String markerKey = "marker";
        String markerTestValue = "MyMarker";
        int drainTimeout = 1;
        String message1 = "Simple log line - "+random(5);
        Marker marker = MarkerFactory.getMarker(markerTestValue);
        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);

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
        String type = "contextResetType" + random(8);
        String loggerName = "ContextResetLogger" + random(8);
        int drainTimeout = 1;

        String message1 = "Before Reset Line - " + random(5);
        String message2 = "After Reset Line - " + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1);
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // This clears everything from the context
        loggerContext.reset();

        // We need to add the appender again
        testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.warn(message2);
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(2);
        logRequest = mockListener.assertLogReceivedByMessage(message2);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.WARN.levelStr);
    }

    @Test
    public void testTokenAndLogzioUrlFromSystemEnvironment() {
        String token = System.getenv("JAVA_HOME");
        String type = "testType" + random(8);
        String loggerName = "testLogger" + random(8);
        int drainTimeout = 1;

        String message1 = "Just a log - " + random(5);
        Logger testLogger = createLogger("$JAVA_HOME", type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
    }

    @Test
    public void checkExactStackTrace() throws Exception {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final MyRunner.ExceptionGenerator exceptionGenerator = new MyRunner.ExceptionGenerator();

        // We need to generate an exception with constant stack trace
        new Thread(new MyRunner(countDownLatch, exceptionGenerator)).start();
        countDownLatch.await();

        String token = "exceptionToken";
        String type = "stacktraceType" + random(8);
        String loggerName = "traceLogger" + random(8);
        int drainTimeout = 1;

        String message1 = "Any line change here can cause the test to break";

        String expectedException = "java.lang.RuntimeException: Got NPE!\n" +
                "\tat io.logz.logback.MyRunner$ExceptionGenerator.generateNPE(MyRunner.java:33)\n" +
                "\tat io.logz.logback.MyRunner.run(MyRunner.java:18)\n" +
                "\tat java.lang.Thread.run(Thread.java:748)\n" +
                "Caused by: java.lang.NullPointerException: null\n" +
                "\tat io.logz.logback.MyRunner$ExceptionGenerator.generateNPE(MyRunner.java:31)\n" +
                "\t... 2 common frames omitted\n";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1, exceptionGenerator.getE());
        sleepSeconds(drainTimeout * 2);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertThat(logRequest.getStringFieldOrNull("exception")).isEqualTo(expectedException);
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

    private Logger createLogger(String token, String type, String loggerName, Integer drainTimeout,
                                boolean addHostname, boolean line, String additionalFields, boolean compressRequests) {
        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}, addHostname={}, line={}, additionalFields={} ",
                loggerName, token, type, drainTimeout, addHostname, line, additionalFields);

        ch.qos.logback.classic.Logger logbackLogger =  (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        Context logbackContext = logbackLogger.getLoggerContext();
        logzioLogbackAppender.setContext(logbackContext);
        logzioLogbackAppender.setToken(token);
        logzioLogbackAppender.setLogzioType(type);
        logzioLogbackAppender.setDebug(true);
        logzioLogbackAppender.setLine(line);
        logzioLogbackAppender.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioLogbackAppender.setAddHostname(addHostname);
        logzioLogbackAppender.setCompressRequests(compressRequests);
        logzioLogbackAppender.setName("LogzioLogbackAppender");
        if (drainTimeout != null) {
            logzioLogbackAppender.setDrainTimeoutSec(drainTimeout);
        }
        if (additionalFields != null) {
            logzioLogbackAppender.setAdditionalFields(additionalFields);
        }
        logzioLogbackAppender.start();
        assertThat(logzioLogbackAppender.isStarted()).isTrue();
        logbackLogger.addAppender(logzioLogbackAppender);
        logbackLogger.setAdditive(false);
        return logbackLogger;
    }

}