package io.logz;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaseTest {

    protected final static Logger logger = LoggerFactory.getLogger(BaseTest.class);
    protected static final String LISTENER_ADDRESS = "localhost";
    protected MockLogzioBulkListener mockListener;
    protected int port;

    @Before
    public void startMockListener() throws Exception {
        int attempts = 1;
        while (attempts <= 3) {
            int availablePort = -1;
            try {
                ServerSocket serverSocket = new ServerSocket(0);
                serverSocket.close();
                availablePort = serverSocket.getLocalPort();;
                mockListener = new MockLogzioBulkListener(LISTENER_ADDRESS, availablePort);
                logger.info("Starting Mock listener on port {}", availablePort);
                mockListener.start();
                port = availablePort;
                break;
            } catch (BindException e) {
                if (attempts++ == 3) {
                    throw new RuntimeException("Failed to get a non busy port: "+availablePort, e);
                } else {
                    logger.info("Failed to start mock listener on port {}", availablePort);
                }
            }
        }
    }

    @After
    public void stopMockListener() {
        logger.info("Stopping Mock listener");
        mockListener.stop();
    }

    protected MockLogzioBulkListener.LogRequest assertLogReceivedByMessage(String message) {
        Optional<MockLogzioBulkListener.LogRequest> logRequest = mockListener.getLogByMessageField(message);
        assertThat(logRequest.isPresent()).describedAs("Log with message '"+message+"' received").isTrue();
        return logRequest.get();
    }

    protected String random(int numberOfChars) {
        return UUID.randomUUID().toString().substring(0, numberOfChars-1);
    }

    protected void sleepSeconds(int seconds) {
        logger.info("Sleeping {} [sec]...", seconds);
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertNumberOfReceivedMsgs(int count) {
        Assertions.assertThat(mockListener.getNumberOfReceivedLogs())
                .describedAs("Messages on mock listener: {}", mockListener.getReceivedMsgs())
                .isEqualTo(count);
    }

    protected void assertLogReceivedIs(String message, String token, String type, String loggerName, Optional<?> level) {
        MockLogzioBulkListener.LogRequest log = assertLogReceivedByMessage(message);
        assertLogReceivedIs(log, token, type, loggerName, level);
    }

    protected void assertLogReceivedIs(MockLogzioBulkListener.LogRequest log, String token, String type, String loggerName, Optional<?> level) {
        Assertions.assertThat(log.getToken()).isEqualTo(token);
        Assertions.assertThat(log.getType()).isEqualTo(type);
        Assertions.assertThat(log.getLogger()).isEqualTo(loggerName);
        if ( level.isPresent()) {
            Assertions.assertThat(log.getLogLevel()).isEqualTo(level.get().toString());
        }
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

    abstract TestSenderWrapper getTestSenderWrapper(String token, String type, String loggerName, Integer drainTimeout, Integer fsPercentThreshold,
                                                    String bufferDir, Integer socketTimeout, boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds, int port) ;



}

