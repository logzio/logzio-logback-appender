package io.logz;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.Context;
import io.logz.logback.LogzioLogbackAppender;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaseTest {

    private final static Logger logger = LoggerFactory.getLogger(BaseTest.class);
    protected static final String LISTENER_ADDRESS = "localhost";
    protected MockLogzioBulkListener mockListener;
    protected int port;
    protected LogzioLogbackAppender logzioLogbackAppender;

    @Before
    public void startMockListener() throws Exception {
        int attempts = 1;
        while (attempts <= 3) {
            try {
                int availablePort = new ServerSocket(0).getLocalPort();;
                mockListener = new MockLogzioBulkListener(LISTENER_ADDRESS, availablePort);
                logger.info("Starting Mock listener on port {}", availablePort);
                mockListener.start();
                port = availablePort;
                break;
            } catch (BindException e) {
                if (attempts++ == 3) {
                    throw new RuntimeException("Failed to get a non busy port", e);
                }
            }
        }
    }

    @After
    public void stopMockListener() {
        logger.info("Stopping Mock listener");
        mockListener.stop();
    }

    protected Logger createLogger(String token, String type, String loggerName, Integer drainTimeout,
                               Integer fsPercentThreshold, String bufferDir, Integer socketTimeout,
                               boolean addHostname, String additionalFields) {
        return createLogger(token, type, loggerName, drainTimeout, fsPercentThreshold,
                bufferDir, socketTimeout, addHostname, additionalFields, null);
    }

    protected Logger createLogger(String token, String type, String loggerName, Integer drainTimeout,
                               Integer fsPercentThreshold, String bufferDir, Integer socketTimeout,
                               boolean addHostname, String additionalFields,
                               Integer gcPersistedQueueFilesIntervalSeconds) {

        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}, fsPercentThreshold={}, bufferDir={}, socketTimeout={}, addHostname={}, additionalFields={}",
                loggerName, token, type, drainTimeout, fsPercentThreshold, bufferDir, socketTimeout, addHostname, additionalFields);

        ch.qos.logback.classic.Logger logbackLogger =  (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);

        Context logbackContext = logbackLogger.getLoggerContext();
        logzioLogbackAppender = new LogzioLogbackAppender();
        logzioLogbackAppender.setContext(logbackContext);
        logzioLogbackAppender.setToken(token);
        logzioLogbackAppender.setLogzioType(type);
        logzioLogbackAppender.setDebug(true);
        logzioLogbackAppender.setLogzioUrl("http://" + LISTENER_ADDRESS + ":" + port);
        logzioLogbackAppender.setAddHostname(addHostname);

        if (drainTimeout != null) {
            logzioLogbackAppender.setDrainTimeoutSec(drainTimeout);
        }
        if (fsPercentThreshold != null) {
            logzioLogbackAppender.setFileSystemFullPercentThreshold(fsPercentThreshold);
        }
        if (bufferDir != null) {
            logzioLogbackAppender.setBufferDir(bufferDir);
        } else {
            File tempDir = TestEnvironment.createTempDirectory();
            tempDir.deleteOnExit();
            logzioLogbackAppender.setBufferDir(tempDir.getAbsolutePath());
        }
        if (socketTimeout != null) {
            logzioLogbackAppender.setSocketTimeout(socketTimeout);
        }

        if (additionalFields != null) {
            logzioLogbackAppender.setAdditionalFields(additionalFields);
        }

        if (gcPersistedQueueFilesIntervalSeconds != null) {
            logzioLogbackAppender.setGcPersistedQueueFilesIntervalSeconds(gcPersistedQueueFilesIntervalSeconds);
        }

        logzioLogbackAppender.start();
        assertThat(logzioLogbackAppender.isStarted()).isTrue();
        logbackLogger.addAppender(logzioLogbackAppender);
        logbackLogger.setAdditive(false);

        return logbackLogger;
    }

    protected String random(int numberOfChars) {
        return UUID.randomUUID().toString().substring(0, numberOfChars-1);
    }

    protected void assertNumberOfReceivedMsgs(int count) {
        assertThat(mockListener.getNumberOfReceivedLogs())
                .describedAs("Messages on mock listener: {}", mockListener.getReceivedMsgs())
                .isEqualTo(count);
    }

    protected void assertLogReceivedIs(String message, String token, String type, String loggerName, Level level) {
        MockLogzioBulkListener.LogRequest log = assertLogReceivedByMessage(message);
        assertLogReceivedIs(log, token, type, loggerName, level);
    }

    protected void assertLogReceivedIs(MockLogzioBulkListener.LogRequest log, String token, String type, String loggerName, Level level) {
        assertThat(log.getToken()).isEqualTo(token);
        assertThat(log.getType()).isEqualTo(type);
        assertThat(log.getLogger()).isEqualTo(loggerName);
        assertThat(log.getLogLevel()).isEqualTo(level.toString());
    }

    protected MockLogzioBulkListener.LogRequest assertLogReceivedByMessage(String message) {
        Optional<MockLogzioBulkListener.LogRequest> logRequest = mockListener.getLogByMessageField(message);
        assertThat(logRequest.isPresent()).describedAs("Log with message '"+message+"' received").isTrue();
        return logRequest.get();
    }

    protected void assertAdditionalFields(MockLogzioBulkListener.LogRequest logRequest, Map<String, String> additionalFields) {
        additionalFields.forEach((field, value) ->  {
            String fieldValueInLog = logRequest.getStringFieldOrNull(field);
            assertThat(fieldValueInLog)
                    .describedAs("Field '{}' in Log [{}]", field, logRequest.getJsonObject().toString())
                    .isNotNull()
                    .isEqualTo(value);
        });
    }

    protected void sleepSeconds(int seconds) {
        logger.info("Sleeping {} [sec]...", seconds);
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
