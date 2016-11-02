package io.logz.logback;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bluejeans.common.bigqueue.BigQueue;
import com.google.common.base.Splitter;
import com.google.gson.JsonObject;
import io.logz.logback.exceptions.LogzioServerErrorException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogzioSender {
    private static final int MAX_SIZE_IN_BYTES = 3 * 1024 * 1024;  // 3 MB
    public static final int INITIAL_WAIT_BEFORE_RETRY_MS = 2000;
    public static final int MAX_RETRIES_ATTEMPTS = 3;

    private static final Map<String, LogzioSender> logzioSenderInstances = new HashMap<>();
    public static final int FINAL_DRAIN_TIMEOUT_SEC = 20;

    private final BigQueue logsBuffer;
    private final File queueDirectory;
    private final URL logzioListenerUrl;
    private HttpURLConnection conn;
    private boolean dontCheckEnoughDiskSpace = false;

    private final String logzioToken;
    private final String logzioType;
    private final int drainTimeout;
    private final int fsPercentThreshold;
    private final String logzioUrl;
    private final int socketTimeout;
    private final int connectTimeout;
    private final boolean debug;
    private final LogzioLogbackAppender.StatusReporter reporter;
    private final ScheduledExecutorService tasksExecutor;
    private final Map<String, String> additionalFieldsMap;

    private final List<String> throwableProxyConversionOptions = Arrays.asList("full");
    private final ThrowableProxyConverter throwableProxyConverter;
    private final int gcPersistedQueueFilesIntervalSeconds;
    private final AtomicBoolean drainRunning = new AtomicBoolean(false);

    private LogzioSender(String logzioToken, String logzioType, int drainTimeout, int fsPercentThreshold, String bufferDir,
                        String logzioUrl, int socketTimeout, int connectTimeout, boolean debug,
                        LogzioLogbackAppender.StatusReporter reporter, ScheduledExecutorService tasksExecutor,
                        boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds)
            throws IllegalArgumentException {

        this.logzioToken = logzioToken;
        this.logzioType = logzioType;
        this.drainTimeout = drainTimeout;
        this.fsPercentThreshold = fsPercentThreshold;
        this.logzioUrl = logzioUrl;
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        this.debug = debug;
        this.reporter = reporter;
        this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
        additionalFieldsMap = new HashMap<>();

        if (this.fsPercentThreshold == -1) {
            dontCheckEnoughDiskSpace = true;
        }

        logsBuffer = new BigQueue(bufferDir, "logzio-logback-appender");
        queueDirectory = new File(bufferDir);

        if (additionalFields != null) {
            JsonObject reservedFieldsTestLogMessage = formatMessageAsJson(new Date().getTime(), "Level", "Message", "Logger", "Thread", Optional.empty(), Optional.empty());
            Splitter.on(';').omitEmptyStrings().withKeyValueSeparator('=').split(additionalFields).forEach((k, v) -> {

                if (reservedFieldsTestLogMessage.get(k) != null) {
                    reporter.warning("The field name '" + k + "' defined in additionalFields configuration can't be used since it's a reserved field name. This field will not be added to the outgoing log messages");
                }
                else {
                    if (v.startsWith("$")) {
                        String environmentValue = System.getenv(v.replace("$", ""));
                        if (environmentValue != null) {
                            additionalFieldsMap.put(k, environmentValue);
                        }
                    } else {
                        additionalFieldsMap.put(k, v);
                    }
                }
            });
            reporter.info("The additional fields that would be added: " + additionalFieldsMap.toString());
        }

        try {
            if (addHostname) {
                String hostname = InetAddress.getLocalHost().getHostName();
                additionalFieldsMap.put("hostname", hostname);
            }
        } catch (UnknownHostException e) {
            reporter.warning("The configuration addHostName was specified but the host could not be resolved, thus the field 'hostname' will not be added", e);
        }

        try {
            logzioListenerUrl = new URL(this.logzioUrl + "/?token=" + this.logzioToken + "&type=" + this.logzioType);

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("For some reason could not initialize URL. Cant recover..");
        }

        this.tasksExecutor = tasksExecutor;

        throwableProxyConverter = new ThrowableProxyConverter();
        throwableProxyConverter.setOptionList(throwableProxyConversionOptions);
        throwableProxyConverter.start();

        debug("Created new LogzioSender class");
    }

    public static synchronized LogzioSender getOrCreateSenderByType(String logzioToken, String logzioType, int drainTimeout, int fsPercentThreshold, String bufferDir,
                                                       String logzioUrl, int socketTimeout, int connectTimeout, boolean debug,
                                                       LogzioLogbackAppender.StatusReporter reporter, ScheduledExecutorService tasksExecutor,
                                                       boolean addHostname, String additionalFields, int gcPersistedQueueFilesIntervalSeconds) {

        // We want one buffer per appender. And the only thing that should be different between appenders, is a type.
        // so that's why I create separate buffers per type.
        // I don't want to force users to configure anything else, but to use the already configured appender.
        // BUT - users not always understand the notion of types at first, and can define multiple appenders on the same type - and this is what I want to protect by this factory.
        LogzioSender logzioSenderInstance = logzioSenderInstances.get(logzioType);

        if (logzioSenderInstance == null) {

            LogzioSender logzioSender = new LogzioSender(logzioToken, logzioType, drainTimeout, fsPercentThreshold,
                    bufferDir, logzioUrl, socketTimeout, connectTimeout, debug, reporter,
                    tasksExecutor, addHostname, additionalFields, gcPersistedQueueFilesIntervalSeconds);

            logzioSenderInstances.put(logzioType, logzioSender);
            return logzioSender;
        } else {
            reporter.warning("Already found appender configured for type " + logzioType + ", re-using the same one.");
            return logzioSenderInstance;
        }
    }

    public void start() {
        tasksExecutor.scheduleWithFixedDelay(this::drainQueueAndSend, 0, drainTimeout, TimeUnit.SECONDS);
        tasksExecutor.scheduleWithFixedDelay(this::gcBigQueue, 0, gcPersistedQueueFilesIntervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        // Creating a scheduled executor, outside of logback to try and drain the queue one last time
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        debug("Got stop request, Submitting a final drain queue task to drain before shutdown");

        try {
            executorService.submit(this::drainQueue).get(FINAL_DRAIN_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            debug("Waited " + FINAL_DRAIN_TIMEOUT_SEC + " seconds, but could not finish draining. quitting.");
        } finally {
            executorService.shutdownNow();
        }
    }

    public void gcBigQueue() {
        try {
            logsBuffer.gc();
        } catch (Throwable e) {
            // We cant throw anything out, or the task will stop, so just swallow all
            reporter.error("Uncaught error from BigQueue.gc()", e);
        }
    }

    public void drainQueueAndSend() {
        try {
            if (drainRunning.get()) {
                debug("Drain is running so we won't run another one in parallel");
                return;
            } else {
                drainRunning.set(true);
            }

            drainQueue();

        } catch (Exception e) {
            // We cant throw anything out, or the task will stop, so just swallow all
            reporter.error("Uncaught error from Logz.io sender", e);
        } finally {
            drainRunning.set(false);
        }
    }

    public void send(ILoggingEvent message) {

        // Shading bigqueue logs. Its irrelevant and super verbose
        if (! message.getLoggerName().contains("io.logz.com.bluejeans.common.bigqueue")) {
            enqueue(formatMessage(message).getBytes());
        }
    }

    private void enqueue(byte[] message) {
        if (isEnoughDiskSpace()) {
            logsBuffer.enqueue(message);
        }
    }

    private boolean isEnoughDiskSpace() {
        if (dontCheckEnoughDiskSpace) {
            return true;
        }

        int actualUsedFsPercent = 100 - ((int) (((double) queueDirectory.getUsableSpace() / queueDirectory.getTotalSpace()) * 100));
        if (actualUsedFsPercent >= fsPercentThreshold) {

            reporter.warning(String.format("Logz.io: Dropping logs, as FS used space on %s is %d percent, and the drop threshold is %d percent",
                    queueDirectory.getAbsolutePath(), actualUsedFsPercent, fsPercentThreshold));

            return false;
        }
        else {
            return true;
        }
    }

    private List dequeueUpToMaxBatchSize() {
        List<FormattedLogMessage> logsList = new ArrayList<FormattedLogMessage>();
        while (!logsBuffer.isEmpty()) {

            logsList.add(new FormattedLogMessage(logsBuffer.dequeue()));
            if (sizeInBytes(logsList) >= MAX_SIZE_IN_BYTES) {
                break;
            }
        }
        return logsList;
    }

    private void drainQueue() {
        debug("Attempting to drain queue");
        if (!logsBuffer.isEmpty()) {
            while (!logsBuffer.isEmpty()) {

                List<FormattedLogMessage> logsList = dequeueUpToMaxBatchSize();

                try {
                    sendToLogzio(logsList);

                } catch (LogzioServerErrorException e) {
                    debug("Could not send log to logz.io: ", e);
                    debug("Will retry in the next interval");

                    // And lets return everything to the queue
                    logsList.forEach((logMessage) -> enqueue(logMessage.getMessage()));

                    // Lets wait for a new interval, something is wrong in the server side
                    break;
                }
                if (Thread.interrupted()) {
                    debug("Stopping drainQueue to thread being interrupted");
                    break;
                }
            }
        }
    }

    private int sizeInBytes(List<FormattedLogMessage> logMessages) {
        int totalSize = 0;
        for (FormattedLogMessage currLog : logMessages) totalSize += currLog.getSize();

        return totalSize;
    }

    private byte[] toNewLineSeparatedByteArray(List<FormattedLogMessage> messages) {

        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream(sizeInBytes(messages));
            for (FormattedLogMessage currMessage : messages) byteOutputStream.write(currMessage.getMessage());
            return byteOutputStream.toByteArray();
        }
        catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    private boolean shouldRetry(int statusCode) {

        boolean shouldRetry = true;

        switch (statusCode) {

            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_BAD_REQUEST:
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                shouldRetry = false;
                break;
        }

        return shouldRetry;
    }

    private void sendToLogzio(List<FormattedLogMessage> messages) throws LogzioServerErrorException {
        try {

            byte[] payload = toNewLineSeparatedByteArray(messages);
            int currentRetrySleep = INITIAL_WAIT_BEFORE_RETRY_MS;

            for (int currTry = 1; currTry <= MAX_RETRIES_ATTEMPTS; currTry++) {

                boolean shouldRetry = true;
                int responseCode = 0;
                String responseMessage = "";
                IOException savedException = null;

                try {
                    conn = (HttpURLConnection) logzioListenerUrl.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-length", String.valueOf(payload.length));
                    conn.setRequestProperty("Content-Type", "text/plain");
                    conn.setReadTimeout(socketTimeout);
                    conn.setConnectTimeout(connectTimeout);
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    conn.getOutputStream().write(payload);

                    responseCode = conn.getResponseCode();
                    responseMessage = conn.getResponseMessage();

                    if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                        reporter.warning("Got 400 from logzio, here is the output: \n " + responseMessage);
                    }
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        reporter.error("Logz.io: Got forbidden! Your token is not right. Unfortunately, dropping logs. Message: " + responseMessage);
                    }

                    shouldRetry = shouldRetry(responseCode);
                }
                catch (IOException e) {
                    savedException = e;
                    debug("Got IO exception - " + e.getMessage());
                }

                if (!shouldRetry) {
                    debug("Successfully sent bulk to logz.io, size: " + payload.length);
                    break;

                } else {

                    if (currTry == MAX_RETRIES_ATTEMPTS) {

                        if (savedException != null) {

                            reporter.error("Got IO exception on the last bulk try to logz.io", savedException);
                        }
                        // Giving up, something is broken on Logz.io side, we will try again later
                        throw new LogzioServerErrorException("Got HTTP " + responseCode + " code from logz.io, with message: " + responseMessage);
                    }

                    debug("Could not send log to logz.io, retry (" + currTry + "/" + MAX_RETRIES_ATTEMPTS + ")");
                    debug("Sleeping for " + currentRetrySleep + " ms and will try again.");
                    Thread.sleep(currentRetrySleep);
                    currentRetrySleep *= 2;
                }
            }

        } catch (InterruptedException e) {
            debug("Got interrupted exception");
            Thread.currentThread().interrupt();
        }
    }

    private void debug(String message) {
        if (debug) {
            reporter.info("DEBUG: " + message);
        }
    }

    private void debug(String message, Throwable e) {
        if (debug) {
            reporter.info("DEBUG: " + message, e);
        }
    }

    private String formatMessage(ILoggingEvent loggingEvent) {

        JsonObject logMessage = formatMessageAsJson(loggingEvent.getTimeStamp(), loggingEvent.getLevel().levelStr,
                loggingEvent.getFormattedMessage(), loggingEvent.getLoggerName(), loggingEvent.getThreadName(),
                Optional.ofNullable(loggingEvent.getMDCPropertyMap()), Optional.ofNullable(loggingEvent));

        // Return the json, while separating lines with \n
        return logMessage.toString() + "\n";
    }

    private JsonObject formatMessageAsJson(long timestamp, String logLevelName, String message, String loggerName, String threadName,
                                           Optional<Map<String, String>> mdcPropertyMap, Optional<ILoggingEvent> loggingEvent) {

        JsonObject logMessage = new JsonObject();

        // Adding MDC first, as I dont want it to collide with any one of the following fields
        if (mdcPropertyMap.isPresent()) {
            mdcPropertyMap.get().forEach(logMessage::addProperty);
        }

        logMessage.addProperty("@timestamp", new Date(timestamp).toInstant().toString());
        logMessage.addProperty("loglevel",logLevelName);
        logMessage.addProperty("message", message);
        logMessage.addProperty("logger", loggerName);
        logMessage.addProperty("thread", threadName);

        if (loggingEvent.isPresent()) {
            if (loggingEvent.get().getThrowableProxy() != null) {
                logMessage.addProperty("exception", throwableProxyConverter.convert(loggingEvent.get()));
            }
        }

        if (additionalFieldsMap != null) {
            additionalFieldsMap.forEach(logMessage::addProperty);
        }
        return logMessage;
    }
}
