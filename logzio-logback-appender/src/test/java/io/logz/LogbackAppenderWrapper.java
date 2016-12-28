package io.logz;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.Context;
import io.logz.logback.LogzioLogbackAppender;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.io.File;
import java.util.Optional;

import static io.logz.BaseTest.LISTENER_ADDRESS;

/**
 * Created by MarinaRazumovsky on 27/12/2016.
 */
public class LogbackAppenderWrapper implements TestSenderWrapper {

    private final static Logger logger = LoggerFactory.getLogger(LogbackAppenderWrapper.class);

    private final ch.qos.logback.classic.Logger logbackLogger;

    private final LogzioLogbackAppender logzioLogbackAppender;

    LogbackAppenderWrapper(String token, String type, String loggerName, Integer drainTimeout,
                           Integer fsPercentThreshold, String bufferDir, Integer socketTimeout,
                           boolean addHostname, String additionalFields,
                           Integer gcPersistedQueueFilesIntervalSeconds, int port) {

        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}, fsPercentThreshold={}, bufferDir={}, socketTimeout={}, addHostname={}, additionalFields={}",
                loggerName, token, type, drainTimeout, fsPercentThreshold, bufferDir, socketTimeout, addHostname, additionalFields);

        logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);

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
        Assertions.assertThat(logzioLogbackAppender.isStarted()).isTrue();
        logbackLogger.addAppender(logzioLogbackAppender);
        logbackLogger.setAdditive(false);
    }


    @Override
    public Optional info(String loggerName, String message) {
        logbackLogger.info(message);
        return Optional.of(Level.INFO);
    }

    @Override
    public Optional info(String loggerName, String message, Throwable exc) {
        logbackLogger.info(message,exc);
        return Optional.of(Level.INFO);
    }

    @Override
    public Optional warn(String loggerName, String message) {
        logbackLogger.warn(message);
        return Optional.of(Level.WARN);
    }

    @Override
    public Optional error(String loggerName, String message) {
        logbackLogger.error(message);
        return Optional.of(Level.ERROR);
    }

    public Optional info(String loggerName, Marker market, String message) {
        logbackLogger.info(market, message);
        return Optional.of(Level.INFO);
    }



    @Override
    public void stop() {
        logzioLogbackAppender.stop();
    }
}
