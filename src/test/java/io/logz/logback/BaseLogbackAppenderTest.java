package io.logz.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import io.logz.test.MockLogzioBulkListener;
import net.logstash.logback.composite.ContextJsonProvider;
import net.logstash.logback.composite.loggingevent.ArgumentsJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author MarinaRazumovsky
 */
public abstract class BaseLogbackAppenderTest {
    protected final static Logger logger = LoggerFactory.getLogger(BaseLogbackAppenderTest.class);
    protected MockLogzioBulkListener mockListener;
    protected enum QueueType { DISK, MEMORY }

    @Before
    public void startMockListener() throws Exception {
        mockListener = new io.logz.test.MockLogzioBulkListener();
        mockListener.start();
    }

    @After
    public void stopMockListener() {
        mockListener.stop();
    }

    protected void sleepSeconds(int seconds) {
        logger.info("Sleeping {} [sec]...", seconds);
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected String random(int numberOfChars) {
        return UUID.randomUUID().toString().substring(0, numberOfChars-1);
    }

    protected void assertAdditionalFields(MockLogzioBulkListener.LogRequest logRequest, Map<String, String> additionalFields) {
        additionalFields.forEach((field, value) -> {
            String fieldValueInLog = logRequest.getStringFieldOrNull(field);
            assertThat(fieldValueInLog)
                    .describedAs("Field '{}' in Log [{}]", field, logRequest.getJsonObject().toString())
                    .isNotNull()
                    .isEqualTo(value);
        });
    }

    protected LoggingEventJsonProviders getCommonJsonProviders() {
        LoggingEventJsonProviders jsonProviders = new LoggingEventJsonProviders();
        jsonProviders.addPattern(new LoggingEventPatternJsonProvider());
        jsonProviders.addArguments(new ArgumentsJsonProvider());
        jsonProviders.addMessage(new MessageJsonProvider());
        jsonProviders.addContext(new ContextJsonProvider<ILoggingEvent>());
        jsonProviders.addMdc(new MdcJsonProvider());
        return jsonProviders;
    }

    protected Logger createLogger(LogzioLogbackAppender logzioLogbackAppender, String token, String type, String loggerName, Integer drainTimeout,
                                boolean addHostname, boolean line, String additionalFields,
                                boolean compressRequests) {
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
        if (logzioLogbackAppender.getEncoder() != null) {
            logzioLogbackAppender.getEncoder().setContext(logbackContext);
            logzioLogbackAppender.getEncoder().start();
        }
        logzioLogbackAppender.start();
        assertThat(logzioLogbackAppender.isStarted()).isTrue();
        logbackLogger.addAppender(logzioLogbackAppender);
        logbackLogger.setAdditive(false);
        return logbackLogger;
    }
}
