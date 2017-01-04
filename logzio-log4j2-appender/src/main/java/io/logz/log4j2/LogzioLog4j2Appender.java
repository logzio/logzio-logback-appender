package io.logz.log4j2;

import io.logz.sender.ILogzioStatusReporter;
import io.logz.sender.LogzioSender;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.Log4jThreadFactory;
import org.apache.logging.log4j.status.StatusLogger;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.Executors;

;

/**
 * Created by MarinaRazumovsky on 13/12/2016.
 */
@Plugin(name = "LogzioAppender", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class LogzioLog4j2Appender extends AbstractAppender {

    private LogzioSender logzioSender;

    private String logzioToken;
    private String logzioType = "java";
    private int drainTimeoutSec = 5;
    private int fileSystemFullPercentThreshold = 98;
    private String bufferDir;
    private String logzioUrl = "https://listener.logz.io:8071";
    private int connectTimeout = 10 * 1000;
    private int socketTimeout = 10 * 1000;
    private boolean debug = false;
    private boolean addHostname = false;
    private String additionalFields;
    private int gcPersistedQueueFilesIntervalSeconds = 30;

    private static Logger statusLogger = StatusLogger.getLogger();

    protected LogzioLog4j2Appender(String name, Filter filter, Layout<? extends Serializable> layout, String url,
                                   String token, String type, int drainTimeoutSec,int fileSystemFullPercentThreshold,
                                   String bufferDir, int socketTimeout, int connectTimeout, boolean addHostname,
                                   String additionalFields, boolean debug,int gcPersistedQueueFilesIntervalSeconds) {
        super(name, filter, layout, true);
        this.logzioToken = token;
        this.logzioUrl = url;
        this.logzioType = type;
        this.drainTimeoutSec = drainTimeoutSec;
        this.fileSystemFullPercentThreshold = fileSystemFullPercentThreshold;
        this.bufferDir = bufferDir;
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        this.debug = debug;
        this.addHostname = addHostname;
        this.additionalFields = additionalFields;
        this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<LogzioLog4j2Appender> {

        @PluginElement("Filter")
        private Filter filter;

        @PluginElement("Layout")
        Layout<? extends Serializable> layout;

        @PluginBuilderAttribute
        @Required(message = "No name provided for LogzioLog4j2Appender")
        String name;

        @PluginBuilderAttribute("logzioURL")
        String logzioURL="https://listener.logz.io:8071";

        @PluginBuilderAttribute
        @Required(message = "No logz.io token provided for LogzioLog4j2Appender")
        String logzioToken;

        @PluginBuilderAttribute
        String logzioType = "java";

        @PluginBuilderAttribute
        int drainTimeoutSec = 5;

        @PluginBuilderAttribute
        int fileSystemFullPercentThreshold = 98;

        @PluginBuilderAttribute
        String bufferDir;

        @PluginBuilderAttribute
        int socketTimeout = 10*1000;

        @PluginBuilderAttribute
        int connectTimeout = 10*1000;;

        @PluginBuilderAttribute
        boolean addHostname = false;

        @PluginBuilderAttribute
        String additionalFields;

        @PluginBuilderAttribute
        boolean debug=false;

        @PluginBuilderAttribute
        int gcPersistedQueueFilesIntervalSeconds = 30;


        @Override
        public LogzioLog4j2Appender build() {
            return new LogzioLog4j2Appender(name,filter, layout, logzioURL, logzioToken, logzioType, drainTimeoutSec, fileSystemFullPercentThreshold,
                    bufferDir,socketTimeout,connectTimeout,addHostname,additionalFields,debug,gcPersistedQueueFilesIntervalSeconds);
        }


        public Builder setFilter(Filter filter) {
            this.filter = filter;
            return this;
        }


        public Builder setLayout(Layout<? extends Serializable> layout) {
            if (this.layout == null) {
                layout = PatternLayout.createDefaultLayout();
            }
            this.layout = layout;
            return this;
        }

        public Builder setName(String name) {
            if (name == null) {
                statusLogger.error("No name provided for MyCustomAppenderImpl");
            }
            this.name = name;
            return this;
        }

        public Builder setLogzioURL(String logzioURL) {
            this.logzioURL = logzioURL;
            return this;
        }

        public Builder setLogzioToken(String logzioToken) {
            if (logzioToken ==null) {
                statusLogger.error("Logz.io Token is missing! Bailing out..");
            }
            this.logzioToken = logzioToken;
            return this;
        }

        public Builder setLogzioType(String logzioType) {
            this.logzioType = logzioType;
            return this;
        }

        public Builder setDrainTimeoutSec(int drainTimeoutSec) {
            this.drainTimeoutSec = drainTimeoutSec;
            return this;
        }

        public Builder setFileSystemFullPercentThreshold(int fileSystemFullPercentThreshold) {
            if (!(fileSystemFullPercentThreshold >= 1 && fileSystemFullPercentThreshold <= 100)) {
                statusLogger.error("fileSystemFullPercentThreshold should be a number between 1 and 100, or -1. Set to default value 98%");
                fileSystemFullPercentThreshold = 98;
            }
            this.fileSystemFullPercentThreshold = fileSystemFullPercentThreshold;
            return this;
        }

        public Builder setBufferDir(String bufferDir) {
            this.bufferDir = bufferDir;
            return this;
        }

        public Builder setSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        public Builder setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder setAddHostname(boolean addHostname) {
            this.addHostname = addHostname;
            return this;
        }

        public Builder setAdditionalFields(String additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }

        public Builder setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder setGcPersistedQueueFilesIntervalSeconds(int gcPersistedQueueFilesIntervalSeconds) {
            this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
            return this;
        }
    }


    public void start() {
        if (bufferDir != null) {
            bufferDir += "/" + logzioType;
            File bufferFile = new File(bufferDir);
            if (bufferFile.exists()) {
                if (!bufferFile.canWrite()) {
                    statusLogger.error("We cant write to your bufferDir location: "+bufferFile.getAbsolutePath());
                    return;
                }
            } else {
                if (!bufferFile.mkdirs()) {
                    statusLogger.error("We cant create your bufferDir location: "+bufferFile.getAbsolutePath());
                    return;
                }
            }
        }
        else {
            bufferDir = System.getProperty("java.io.tmpdir") + "/logzio-log4j2-buffer/" + logzioType;
        }

        try {
            ILogzioStatusReporter reporter = new StatusReporter();
            logzioSender = LogzioSender.getOrCreateSenderByType(logzioToken, logzioType, drainTimeoutSec, fileSystemFullPercentThreshold,
                    bufferDir, logzioUrl, socketTimeout, connectTimeout, debug,
                    reporter, Executors.newScheduledThreadPool (2,Log4jThreadFactory.createThreadFactory(this.getClass().getSimpleName())),
                    gcPersistedQueueFilesIntervalSeconds, new LogzioLog4j2JsonFormatter(additionalFields, addHostname, reporter));
            logzioSender.start();
            super.start();
        }
        catch (IllegalArgumentException e) {
            statusLogger.error("Something unexpected happened while generating connection to logz.io");
            statusLogger.error("Exception: " + e.getMessage(), e);
            return;  // Not signaling super as up, we have something we cant deal with.
        }
    }


    @Override
    public void stop() {
        if (logzioSender != null) logzioSender.stop();
        super.stop();
    }


    @Override
    public void append(LogEvent logEvent) {
        logzioSender.send(logEvent);
    }

    class StatusReporter implements ILogzioStatusReporter {

        @Override
        public void error(String msg) {
            statusLogger.error(msg);
        }

        @Override
        public void error(String msg, Throwable e) {
            statusLogger.error(msg,e);
        }

        @Override
        public void warning(String msg) {
            statusLogger.warn(msg);
        }

        @Override
        public void warning(String msg, Throwable e) {
            statusLogger.warn(msg,e);
        }

        @Override
        public void info(String msg) {
            statusLogger.info(msg);
        }

        @Override
        public void info(String msg, Throwable e) {
            statusLogger.info(msg,e);
        }
    }
}
