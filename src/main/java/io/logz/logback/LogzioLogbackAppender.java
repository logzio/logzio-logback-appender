package io.logz.logback;

import ch.qos.logback.classic.pattern.LineOfCallerConverter;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.google.common.base.Splitter;
import io.logz.sender.HttpsRequestConfiguration;
import io.logz.sender.LogzioSender;
import io.logz.sender.SenderStatusReporter;
import io.logz.sender.com.google.gson.Gson;
import io.logz.sender.com.google.gson.JsonElement;
import io.logz.sender.com.google.gson.JsonObject;
import io.logz.sender.exceptions.LogzioParameterErrorException;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LogzioLogbackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final Gson gson = new Gson();
    private static final String TIMESTAMP = "@timestamp";
    private static final String LOGLEVEL = "loglevel";
    private static final String MARKER = "marker";
    private static final String MESSAGE = "message";
    private static final String LOGGER = "logger";
    private static final String LINE = "line";
    private static final String THREAD = "thread";
    private static final String EXCEPTION = "exception";
    private static final String FORMAT_TEXT = "text";
    private static final String FORMAT_JSON = "json";
    private static final String FORMAT_IGNORE = "ignore";
    private static final int DONT_LIMIT_QUEUE_SPACE = -1;
    private static final int LOWER_PERCENTAGE_FS_SPACE = 1;
    private static final int UPPER_PERCENTAGE_FS_SPACE = 100;

    private static final Set<String> reservedFields =  new HashSet<>(Arrays.asList(new String[] {TIMESTAMP,LOGLEVEL, MARKER, MESSAGE,LOGGER,THREAD,EXCEPTION}));

    private LogzioSender logzioSender;
    private ThrowableProxyConverter throwableProxyConverter;
    private LineOfCallerConverter lineOfCallerConverter;
    private Map<String, String> additionalFieldsMap = new HashMap<>();

    // User controlled variables
    private String logzioToken;
    private String logzioType = "java";
    private int drainTimeoutSec = 5;
    private int fileSystemFullPercentThreshold = 98;
    private String queueDir;
    private String logzioUrl;
    private int connectTimeout = 10 * 1000;
    private int socketTimeout = 10 * 1000;
    private boolean debug = false;
    private boolean addHostname = false;
    private boolean line = false;
    private boolean compressRequests = false;
    private boolean inMemoryQueue = false;
    private long inMemoryQueueCapacityBytes = 100 * 1024 * 1024;
    private int gcPersistedQueueFilesIntervalSeconds = 30;
    private String format = FORMAT_TEXT;

    public LogzioLogbackAppender() {
        super();
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setToken(String logzioToken) {
        this.logzioToken = getValueFromSystemEnvironmentIfNeeded(logzioToken);
    }

    public void setLogzioType(String logzioType) {
        this.logzioType = logzioType;
    }

    public void setDrainTimeoutSec(int drainTimeoutSec) {
        // Basic protection from running negative or zero timeout
        if (drainTimeoutSec < 1) {
            this.drainTimeoutSec = 1;
            addInfo("Got unsupported drain timeout " + drainTimeoutSec + ". The timeout must be number greater then 1. I have set to 1 as fallback.");
        } else {
            this.drainTimeoutSec = drainTimeoutSec;
        }
    }

    public void setFileSystemFullPercentThreshold(int fileSystemFullPercentThreshold) {
        this.fileSystemFullPercentThreshold = fileSystemFullPercentThreshold;
    }

    /**
     * @param bufferDir: queue dir path
     * @deprecated use {@link #setQueueDir(String)}
     */
    @Deprecated
    public void setBufferDir(String bufferDir) {
        this.queueDir = bufferDir;
    }

    public void setQueueDir(String queueDir) {
        this.queueDir = queueDir;
    }

    public void setLogzioUrl(String logzioUrl) {
        this.logzioUrl = getValueFromSystemEnvironmentIfNeeded(logzioUrl);
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setInMemoryQueue(boolean inMemoryQueue) {
        this.inMemoryQueue = true;
    }

    public boolean isInMemoryQueue() {
        return inMemoryQueue;
    }

    public void setInMemoryQueueCapacityBytes(long inMemoryQueueCapacityBytes) {
        this.inMemoryQueueCapacityBytes = inMemoryQueueCapacityBytes;
    }

    public long getInMemoryQueueCapacityBytes() {
        return inMemoryQueueCapacityBytes;
    }

    public boolean isCompressRequests() { return compressRequests; }

    public void setCompressRequests(boolean compressRequests) { this.compressRequests = compressRequests; }

    public void setAdditionalFields(String additionalFields) {
       if (additionalFields != null) {
           Splitter.on(';').omitEmptyStrings().withKeyValueSeparator('=').split(additionalFields).forEach((k, v) -> {
               if (reservedFields.contains(k)) {
                   addWarn("The field name '" + k + "' defined in additionalFields configuration can't be used since it's a reserved field name. This field will not be added to the outgoing log messages");
               } else {
                   String value = getValueFromSystemEnvironmentIfNeeded(v);
                   if (value != null) {
                       additionalFieldsMap.put(k, value);
                   }
               }
           });
           addInfo("The additional fields that would be added: " + additionalFieldsMap.toString());
       }
    }

    public boolean isAddHostname() {
        return addHostname;
    }

    public void setAddHostname(boolean addHostname) {
        this.addHostname = addHostname;
    }

    public boolean isLine() {
        return line;
    }

    public void setLine(boolean line) {
        this.line = line;
    }

    public void setGcPersistedQueueFilesIntervalSeconds(int gcPersistedQueueFilesIntervalSeconds) {
        this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
    }

    @Override
    public void start() {
        setHostname();
        HttpsRequestConfiguration conf;
        try {
            conf = getHttpsRequestConfiguration();
        } catch (LogzioParameterErrorException e) {
            addError("Some of the configuration parameters of logz.io is wrong: " + e.getMessage(), e);
            return;
        }
        LogzioSender.Builder logzioSenderBuilder = getSenderBuilder(conf);
        if (inMemoryQueue) {
            if (!validateInMemoryQueueCapacityBytes()) {
                return;
            }
            logzioSenderBuilder
                .withInMemoryQueue()
                    .setCapacityInBytes(inMemoryQueueCapacityBytes)
                .endInMemoryQueue();
        } else {
            if (!validateFsPercentThreshold()) {
                return;
            }
            File queueDirFile = getQueueFile();
            if (queueDirFile == null) {
                return;
            }
            logzioSenderBuilder
                    .withDiskQueue()
                        .setQueueDir(queueDirFile)
                        .setGcPersistedQueueFilesIntervalSeconds(gcPersistedQueueFilesIntervalSeconds)
                        .setFsPercentThreshold(fileSystemFullPercentThreshold)
                    .endDiskQueue();
        }
        try {
            logzioSender = logzioSenderBuilder.build();
        } catch (LogzioParameterErrorException e) {
            addError("Could not create logzio sender", e);
            return;
        }

        logzioSender.start();
        throwableProxyConverter = new ThrowableProxyConverter();
        lineOfCallerConverter = new LineOfCallerConverter();
        throwableProxyConverter.setOptionList(Arrays.asList("full"));
        throwableProxyConverter.start();
        super.start();
    }

    private LogzioSender.Builder getSenderBuilder(HttpsRequestConfiguration conf) {
        return LogzioSender
                    .builder()
                    .setDebug(debug)
                    .setDrainTimeoutSec(drainTimeoutSec)
                    .setHttpsRequestConfiguration(conf)
                    .setReporter(new StatusReporter())
                    .setTasksExecutor(context.getScheduledExecutorService());
    }

    private File getQueueFile() {
        if (queueDir != null) {
            queueDir += File.separator + logzioType;
            File queueFile = new File(queueDir);
            if (queueFile.exists()) {
                if (!queueFile.canWrite()) {
                    addError("We cant write to your queueDir location: " + queueFile.getAbsolutePath());
                    return null;
                }
            } else {
                if (!queueFile.mkdirs()) {
                    addError("We cant create your queueDir location: " + queueFile.getAbsolutePath());
                    return null;
                }
            }
        } else {
            queueDir = System.getProperty("java.io.tmpdir") + File.separator + "logzio-logback-queue" + File.separator + logzioType;
        }
        return new File(queueDir,"logzio-logback-appender");
    }

    private boolean validateInMemoryQueueCapacityBytes() {
        if (inMemoryQueueCapacityBytes <= 0 && inMemoryQueueCapacityBytes != DONT_LIMIT_QUEUE_SPACE) {
            addError("inMemoryQueueCapacityBytes should be a non zero integer or -1");
            return false;
        }
        return true;
    }

    private boolean validateFsPercentThreshold() {
        if (!(fileSystemFullPercentThreshold >= LOWER_PERCENTAGE_FS_SPACE && fileSystemFullPercentThreshold <= UPPER_PERCENTAGE_FS_SPACE)) {
            if (fileSystemFullPercentThreshold != DONT_LIMIT_QUEUE_SPACE) {
                addError("fileSystemFullPercentThreshold should be a number between 1 and 100, or -1");
                return false;
            }
        }
        return true;
    }

    private void setHostname() {
        try {
            if (addHostname) {
                String hostname = InetAddress.getLocalHost().getHostName();
                additionalFieldsMap.put("hostname", hostname);
            }
        } catch (UnknownHostException e) {
            addWarn("The configuration addHostName was specified but the host could not be resolved, thus the field 'hostname' will not be added", e);
        }
    }

    private HttpsRequestConfiguration getHttpsRequestConfiguration() throws LogzioParameterErrorException {
        return HttpsRequestConfiguration
                .builder()
                .setLogzioListenerUrl(logzioUrl)
                .setSocketTimeout(socketTimeout)
                .setLogzioType(logzioType)
                .setLogzioToken(logzioToken)
                .setConnectTimeout(connectTimeout)
                .setCompressRequests(compressRequests)
                .build();
    }

    @Override
    public void stop() {
        if (logzioSender != null) logzioSender.stop();
        if (throwableProxyConverter != null) throwableProxyConverter.stop();
        super.stop();
    }

    private String getValueFromSystemEnvironmentIfNeeded(String value) {
        if (value != null && value.startsWith("$")) {
            String variableName = value.replace("$", "");
            String envVariable = System.getenv(variableName);

            if (envVariable == null || envVariable.isEmpty()) {
                envVariable = System.getProperty(variableName);
            }
            return envVariable;
        }
        return value;
    }

    private JsonObject formatMessageAsJson(ILoggingEvent loggingEvent) {
        JsonObject logMessage;

        if (format.equals(FORMAT_JSON)) {
            try {
                JsonElement jsonElement = gson.fromJson(loggingEvent.getFormattedMessage(), JsonElement.class);
                logMessage = jsonElement.getAsJsonObject();
            } catch (Exception e) {
                logMessage = new JsonObject();
                logMessage.addProperty(MESSAGE, loggingEvent.getFormattedMessage());
            }
        } else {
            logMessage = new JsonObject();
            logMessage.addProperty(MESSAGE, loggingEvent.getFormattedMessage());
        }

        // Adding MDC first, as I dont want it to collide with any one of the following fields
        if (loggingEvent.getMDCPropertyMap() != null) {
            loggingEvent.getMDCPropertyMap().forEach(logMessage::addProperty);
        }

        logMessage.addProperty(TIMESTAMP, new Date(loggingEvent.getTimeStamp()).toInstant().toString());
        logMessage.addProperty(LOGLEVEL,loggingEvent.getLevel().levelStr);

        if (loggingEvent.getMarker() != null) {
            logMessage.addProperty(MARKER, loggingEvent.getMarker().toString());
        }

        logMessage.addProperty(LOGGER, loggingEvent.getLoggerName());
        logMessage.addProperty(THREAD, loggingEvent.getThreadName());
        if (line) {
            logMessage.addProperty(LINE, lineOfCallerConverter.convert(loggingEvent));
        }

        if (loggingEvent.getThrowableProxy() != null) {
            logMessage.addProperty(EXCEPTION, throwableProxyConverter.convert(loggingEvent));
        }

        if (additionalFieldsMap != null) {
            additionalFieldsMap.forEach(logMessage::addProperty);
        }

        return logMessage;
    }

    @Override
    protected void append(ILoggingEvent loggingEvent) {
        if (!loggingEvent.getLoggerName().contains("io.logz.sender")) {
            logzioSender.send(formatMessageAsJson(loggingEvent));
        }
    }

    private class StatusReporter implements SenderStatusReporter {

        @Override
        public void error(String msg) {
            addError(msg);
        }

        @Override
        public void error(String msg, Throwable e) {
            addError(msg, e);
        }

        @Override
        public void warning(String msg) {
            addWarn(msg);
        }

        @Override
        public void warning(String msg, Throwable e) {
            addWarn(msg, e);
        }

        @Override
        public void info(String msg) {
            addInfo(msg);
        }

        @Override
        public void info(String msg, Throwable e) {
            addInfo(msg,e);
        }
    }

}
