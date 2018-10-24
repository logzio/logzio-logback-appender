package io.logz.logback;

import ch.qos.logback.classic.Level;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

public class InMemoryTest extends BaseLogbackAppenderTest {
    private LogzioLogbackAppender logzioLogbackAppender;

    @Before
    public void setUp() {
        logzioLogbackAppender = new LogzioLogbackAppender();
        logzioLogbackAppender.setInMemoryQueue(true);
    }

    @Test
    public void validateQueueCapacity() {
        String token = "verifyCapacityInBytesToken";
        String type = random(8);
        String loggerName = "verifyCapacityInBytes" + random(8);
        int drainTimeout = 2;
        String message1 = "Testing.." + random(5);
        String message2 = "Don't get here test! ";

        logzioLogbackAppender.setInMemoryQueueCapacityBytes(message1.getBytes(StandardCharsets.UTF_8).length);
        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(1);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.levelStr);
    }

    @Test
    public void validateQueueLogCountCapacity() {
        String token = "verifyLogCountCapacity";
        String type = random(8);
        String loggerName = "verifyLogCountCapacity" + random(8);
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Don't get here test! ";

        logzioLogbackAppender.setInMemoryLogsCountCapacity(1);
        Logger testLogger = createLogger(logzioLogbackAppender, token, type, loggerName, drainTimeout, false, false, null, false);

        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(1);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.levelStr);
    }
}

