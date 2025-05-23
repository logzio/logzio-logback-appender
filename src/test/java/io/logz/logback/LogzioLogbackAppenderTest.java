package io.logz.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.DeferredProcessingAware;
import io.logz.sender.com.google.gson.Gson;
import io.logz.test.MockLogzioBulkListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.AbstractPatternJsonProvider;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import org.junit.Before;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;


@RunWith(Parameterized.class)
public class LogzioLogbackAppenderTest extends BaseLogbackAppenderTest {
    private LogzioLogbackAppender logzioLogbackAppender;
    private QueueType queueType;

    @Before
    public void setUp() {
        logzioLogbackAppender = new LogzioLogbackAppender();
        if (queueType == QueueType.MEMORY) {
            logzioLogbackAppender.setInMemoryQueue(true);
        }
    }

    @Parameterized.Parameters
    public static Collection<QueueType[]> logzioSenderBuilders() {
        Collection<QueueType[]> queueTypes = new ArrayList<>();
        for (QueueType type : QueueType.values()) {
            queueTypes.add(new QueueType[]{type});
        }
        return queueTypes;
    }

    public LogzioLogbackAppenderTest(QueueType queueType) {
        this.queueType = queueType;
    }

    /**
     * Initializes the OpenTelemetry SDK with a logging span exporter and the W3C Trace Context
     * propagator.
     *
     * @return A ready-to-use {@link OpenTelemetry} instance.
     */
    protected OpenTelemetry initOpenTelemetry() {
        SdkTracerProvider sdkTracerProvider =
                SdkTracerProvider.builder()
                        .build();

        OpenTelemetrySdk sdk =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(sdkTracerProvider)
                        .build();

        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
        return sdk;
    }

    @Test
    public void openTelemetryContext_Enabled() {
        String token = "token";
        String type = "type" + random(8);
        String loggerName = "otelLogger" + random(8);
        int drainTimeout = 1;
        logzioLogbackAppender.setAddOpentelemetryContext(true);
        Logger testLogger = createLogger(
                logzioLogbackAppender,
                token,
                type,
                loggerName,
                drainTimeout,
                false,
                false,
                null,
                false
        );

        OpenTelemetry openTelemetry = initOpenTelemetry();
        Tracer tracer = openTelemetry.getTracer("test");
        Span span = tracer.spanBuilder("test").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String message = "Simple log line - "+random(5);
            testLogger.info(message);
            sleepSeconds(drainTimeout * 2);
            MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message);
            mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
            String traceId = logRequest.getStringFieldOrNull("trace_id");
            String spanId = logRequest.getStringFieldOrNull("span_id");
            String serviceName = logRequest.getStringFieldOrNull("service_name");

            assertThat(traceId)
                    .as("Check 'trace_id' is present when addOpentelemetryContext=true")
                    .isNotNull();
            assertThat(spanId)
                    .as("Check 'span_id' is present when addOpentelemetryContext=true")
                    .isNotNull();
            assertThat(serviceName)
                    .as("Check 'service_name' is present when addOpentelemetryContext=true")
                    .isNotNull();
        } finally {
            span.end();
        }
    }

    @Test
    public void openTelemetryContext_Disabled() {
        String token = "token";
        String type = "type" + random(8);
        String loggerName = "otelLogger" + random(8);
        int drainTimeout = 1;

        logzioLogbackAppender.setAddOpentelemetryContext(false);
        Logger testLogger = createLogger(
                logzioLogbackAppender,
                token,
                type,
                loggerName,
                drainTimeout,
                false,
                false,
                null,
                false
        );
        OpenTelemetry openTelemetry = initOpenTelemetry();
        Tracer tracer = openTelemetry.getTracer("test");
        Span span = tracer.spanBuilder("test").startSpan();

        try (Scope scope = span.makeCurrent()) {
            String message = "Simple log line - "+random(5);
            testLogger.info(message);

            sleepSeconds(drainTimeout * 2);

            mockListener.assertNumberOfReceivedMsgs(1);
            MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message);
            mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

            String traceId = logRequest.getStringFieldOrNull("trace_id");
            String spanId = logRequest.getStringFieldOrNull("span_id");
            String serviceName = logRequest.getStringFieldOrNull("service_name");

            assertThat(traceId)
                    .as("Check 'trace_id' should NOT be present when addOpentelemetryContext=false")
                    .isNull();
            assertThat(spanId)
                    .as("Check 'span_id' should NOT be present when addOpentelemetryContext=false")
                    .isNull();
            assertThat(serviceName)
                    .as("Check 'service_name' should NOT be present when addOpentelemetryContext=false")
                    .isNull();
        } finally {
            span.end();
        }
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
        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

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

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);
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

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, true);
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
        String additionalFieldsString = "java_home=java_home;testing=yes;message=override";
        additionalFields.put("java_home", "java_home");
        additionalFields.put("testing", "yes");

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, additionalFieldsString, false);
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
        String type = "withOrWithoutHostname" + random(8);
        String loggerName = "runningOutOfIdeasHere" + random(8);
        int drainTimeout = 1;
        String message1 = "Hostname log - " +  random(5);

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, true, false, null, false);
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
        String message1 = "Hostname log - " +  random(5);

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, true, null, false);
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

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

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
        String message1 = "Simple log line - "+random(5);
        String mdcKey = "mdc-key";
        String mdcValue = "mdc-value";

        Map<String, String> mdcKv = new HashMap<>();
        mdcKv.put(mdcKey, mdcValue);
        mdcKv.put("logger", "Doesn't matter");
        MDC.put(mdcKey, mdcValue);

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);
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
        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

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

        String message1 = "Before Reset Line - "+random(5);
        String message2 = "After Reset Line - "+random(5);

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1);
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // This clears everything from the context
        loggerContext.reset();

        // We need to add the appender again
        testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.warn(message2);
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(2);
        logRequest = mockListener.assertLogReceivedByMessage(message2);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.WARN.levelStr);
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

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1, exceptionGenerator.getE());
        sleepSeconds(drainTimeout * 2);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
        assertThat(logRequest.getStringFieldOrNull("exception")).contains("java.lang.RuntimeException: Got NPE!");
        assertThat(logRequest.getStringFieldOrNull("exception")).contains("at io.logz.logback.MyRunner$ExceptionGenerator.generateNPE(MyRunner.java:33)");
        assertThat(logRequest.getStringFieldOrNull("exception")).contains("at io.logz.logback.MyRunner.run(MyRunner.java:18)");
        assertThat(logRequest.getStringFieldOrNull("exception")).contains("Caused by: java.lang.NullPointerException");
    }

    @Test
    public void testTokenAndLogzioUrlFromSystemEnvironment() {
        String token = "token";
        String type = "testType" + random(8);
        String loggerName = "testLogger" + random(8);
        int drainTimeout = 1;

        String message1 = "Just a log - " + random(5);
        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
    }

    @Test
    public void testEncoder() {
        String token = "testEncoder";
        String type = "testEncoder" + random(8);
        String loggerName = "testEncoder" + random(8);
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);

        LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
        encoder.getProviders().addProvider(new LogLevelJsonProvider());
        encoder.getProviders().addProvider(new MessageJsonProvider());
        encoder.getProviders().addProvider(withPattern(String.format("{ \"loglevel\": \"%s\" }", "INFO"), new LoggingEventPatternJsonProvider()));
        encoder.getProviders().addProvider(withPattern(String.format("{ \"type\": \"%s\" }", type), new LoggingEventPatternJsonProvider()));
        encoder.getProviders().addProvider(withPattern(String.format("{ \"logger\": \"%s\" }", loggerName), new LoggingEventPatternJsonProvider()));
        encoder.getProviders().addProvider(withName("timestamp", new LoggingEventFormattedTimestampJsonProvider()));

        logzioLogbackAppender.setEncoder(encoder);

        Logger testLogger = createLogger(logzioLogbackAppender, "testEncoder", type, loggerName, drainTimeout, false, false, null, false);
        encoder.start();

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
    }

    @Test
    public void testDrainQueue() {
        String token = "drainToken";
        String type = "drainType" + random(8);
        String loggerName = "drainLogger" + random(8);
        int drainTimeout = -1;
        String message = "Simple log line - "+random(5);

        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message);
        mockListener.assertNumberOfReceivedMsgs(0);

        logzioLogbackAppender.drainQueueAndSend();
        sleepSeconds(1);
        mockListener.assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);
    }

    private AbstractPatternJsonProvider<ILoggingEvent> withPattern(String pattern,
                                                                   AbstractPatternJsonProvider<ILoggingEvent> provider) {
        provider.setPattern(pattern);
        return provider;
    }

    private <T extends DeferredProcessingAware> AbstractFieldJsonProvider<T> withName(String name,
                                                                                      AbstractFieldJsonProvider<T> provider) {
        provider.setFieldName(name);
        return provider;
    }

    @Test
    public void testCustomExecutor() throws InterruptedException {
        String token = "customExecutorToken"  + random(8);;
        String type = "customExecutorType" + random(8);
        String loggerName = "customExecutorLogger" + random(8);
        int drainTimeout = 2;
        String message = "Testing custom executor - " + random(5);

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("my-custom-logzio-sender-%d")
                .setDaemon(true)
                .build();
        ScheduledExecutorService customExecutor = Executors.newScheduledThreadPool(2, threadFactory); // Example: Pool with 2 threads

        try {
            this.logzioLogbackAppender.setExecutor(customExecutor);
            assertThat(this.logzioLogbackAppender.isStarted()).isFalse();

            Logger testLogger = createLogger(
                    this.logzioLogbackAppender,
                    token,
                    type,
                    loggerName,
                    drainTimeout, 
                    false,  
                    false,
                    null,
                    false
            );

            testLogger.info(message);

            sleepSeconds(drainTimeout * 2);

            mockListener.assertNumberOfReceivedMsgs(1);
            MockLogzioBulkListener.LogRequest logRequest = mockListener.assertLogReceivedByMessage(message);
            mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.levelStr);

            logzioLogbackAppender.stop();
            assertThat(customExecutor.isShutdown())
                    .describedAs("Custom executor should NOT be shut down by appender stop()")
                    .isFalse();
            assertThat(customExecutor.isTerminated())
                    .describedAs("Custom executor should NOT be terminated by appender stop()")
                    .isFalse();

        } finally {
            if (customExecutor != null) {
                customExecutor.shutdown();
                if (!customExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Custom executor did not terminate gracefully, forcing shutdown.");
                    customExecutor.shutdownNow();
                }
            }
        }
    }
}