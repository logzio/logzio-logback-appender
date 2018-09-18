package io.logz.logback;

import ch.qos.logback.core.Context;
import io.logz.test.MockLogzioBulkListener;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author MarinaRazumovsky
 */
public abstract class BaseLogbackAppenderTest {

    private final static Logger logger = LoggerFactory.getLogger(BaseLogbackAppenderTest.class);

    MockLogzioBulkListener mockListener;

    @Before
    public void startMockListener() throws Exception {
        mockListener = new io.logz.test.MockLogzioBulkListener();
        mockListener.start();
    }

    @After
    public void stopMockListener() {
        mockListener.stop();
    }

    void sleepSeconds(int seconds) {
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
}
