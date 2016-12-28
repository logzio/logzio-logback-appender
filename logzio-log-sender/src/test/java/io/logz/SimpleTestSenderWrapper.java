package io.logz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.io.File;
import java.util.Optional;

/**
 * Created by MarinaRazumovsky on 27/12/2016.
 */
public class SimpleTestSenderWrapper implements TestSenderWrapper {

    protected final static Logger logger = LoggerFactory.getLogger(SimpleTestSenderWrapper.class);

    protected SimpleTestSender testLogzioSender;

    SimpleTestSenderWrapper(String token, String type, String loggerName, Integer drainTimeout, Integer fsPercentThreshold, String bufferDir, Integer socketTimeout, int port) {
        if (bufferDir == null) {
            File tempDir = TestEnvironment.createTempDirectory();
            tempDir.deleteOnExit();
            bufferDir = tempDir.getAbsolutePath();
        }
        testLogzioSender = new SimpleTestSender(token,type,drainTimeout,fsPercentThreshold,bufferDir,
                "http://" + BaseTest.LISTENER_ADDRESS + ":" + port,socketTimeout,socketTimeout,true, 30);
    }


    @Override
    public Optional info(String loggerName, String message) {
        testLogzioSender.sendMessage(new SimpleTestMessage(message,loggerName,System.currentTimeMillis()));
        return Optional.empty();
    }

    @Override
    public Optional warn(String loggerName, String message) {
        testLogzioSender.sendMessage(new SimpleTestMessage(message,loggerName,System.currentTimeMillis()));
        return Optional.empty();
    }

    @Override
    public Optional error(String loggerName, String message) {
        testLogzioSender.sendMessage(new SimpleTestMessage(message,loggerName,System.currentTimeMillis()));
        return Optional.empty();
    }

    @Override
    public Optional info(String loggerName, String message, Throwable exc) {
        testLogzioSender.sendMessage(new SimpleTestMessage(
                message+" exception:"+exc.getMessage(),loggerName,System.currentTimeMillis()));
        return Optional.empty();
    }

    public Optional info(String loggerName, Marker market, String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
        logger.info("Stop sender");
        if ( testLogzioSender != null )
            testLogzioSender.stop();
    }

}
