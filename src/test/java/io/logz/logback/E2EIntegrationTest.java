package io.logz.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

/**
 * End-to-end integration test that sends logs to Logz.io.
 * 
 * This test is designed to be run in CI with the following environment variables:
 * - LOGZIO_TOKEN: The Logz.io shipping token
 * - ENV_ID: A unique identifier for this test run (used for log isolation)
 * 
 * The test sends logs to Logz.io and exits. A separate CI step queries the
 * Logz.io API to validate the logs were received.
 */
public class E2EIntegrationTest {
    
    private static final int LOG_COUNT = 5;
    private static final int DRAIN_TIMEOUT_SEC = 5;
    private static final int POST_DRAIN_WAIT_MS = 2000;
    private static final String LOGZIO_LISTENER_URL = "https://listener.logz.io:8071";
    
    public static void main(String[] args) {
        String token = System.getenv("LOGZIO_TOKEN");
        String envId = System.getenv("ENV_ID");
        
        if (token == null || token.isEmpty()) {
            System.err.println("ERROR: LOGZIO_TOKEN environment variable is not set");
            System.exit(1);
        }
        
        if (envId == null || envId.isEmpty()) {
            System.err.println("ERROR: ENV_ID environment variable is not set");
            System.exit(1);
        }
        
        try {
            sendTestLogs(token, envId);
            System.out.println("✅ Successfully sent " + LOG_COUNT + " test logs with env_id: " + envId);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to send test logs: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void sendTestLogs(String token, String envId) throws InterruptedException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        LogzioLogbackAppender appender = new LogzioLogbackAppender();
        appender.setContext(context);
        appender.setToken(token);
        appender.setLogzioType(envId);
        appender.setLogzioUrl(LOGZIO_LISTENER_URL);
        appender.setAddHostname(true);
        appender.setDrainTimeoutSec(DRAIN_TIMEOUT_SEC);
        appender.setAdditionalFields("env_id=" + envId);
        appender.setName("E2ETestAppender");
        appender.start();
        
        if (!appender.isStarted()) {
            throw new RuntimeException("Failed to start LogzioLogbackAppender");
        }
        
        Logger logger = (Logger) LoggerFactory.getLogger(E2EIntegrationTest.class);
        logger.addAppender(appender);
        logger.setAdditive(false);
        
        for (int i = 0; i < LOG_COUNT; i++) {
            logger.info("E2E test message {} - env_id: {}", i, envId);
            Thread.sleep(100);
        }
        
        appender.drainQueueAndSend();
        Thread.sleep(POST_DRAIN_WAIT_MS);
        appender.stop();
    }
}

