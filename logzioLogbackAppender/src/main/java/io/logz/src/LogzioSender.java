package io.logz.src;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bluejeans.common.bigqueue.BigQueue;
import io.logz.src.exceptions.LogzioServerErrorException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.UnexpectedException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by roiravhon on 5/9/16.
 */
public class LogzioSender {


    private final int MAX_SIZE_IN_BYTES = 3145728;  // 3 MB
    private final int INITIAL_WAIT_BEFORE_RETRY = 2000;
    private final int MAX_NUMBER_OF_RETRIES = 3;

    private final ScheduledThreadPoolExecutor tasksExecutor;

    private String queueDir;
    private BigQueue logsBuffer;
    private File queueFile;
    private URL logzioListener;
    private HttpURLConnection conn;

    private String logzioToken;
    private String logzioType;
    private int drainTimeout;
    private int fsPercentThreshold;
    private String logzioUrl;

    public LogzioSender(String logzioToken, String logzioType, int drainTimeout, int fsPercentThreshold, String bufferDir, String logzioUrl) throws UnexpectedException {

        try {
            this.logzioToken = logzioToken;
            this.logzioType = logzioType;
            this.drainTimeout = drainTimeout;
            this.fsPercentThreshold = fsPercentThreshold;
            this.logzioUrl = logzioUrl;

            // Defaulting to temp dir
            if (bufferDir == null) {
                queueDir = System.getProperty("java.io.tmpdir") + "/logzio-logback-buffer";
            }
            else {
                queueDir = bufferDir;
            }

            logsBuffer = new BigQueue(queueDir, "logzio-logback-appender");
            queueFile = new File(queueDir);

            logzioListener = new URL(this.logzioUrl + "/?token=" + this.logzioToken + "&type=" + this.logzioType);

        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new UnexpectedException("For some reason could not initialize URL. Cant recover..");
        } finally {

            tasksExecutor = new ScheduledThreadPoolExecutor(1);
        }
    }

    public void start() {

        tasksExecutor.scheduleWithFixedDelay(this::run, 0, drainTimeout, TimeUnit.SECONDS);
    }

    public void stop() {

        try {
            tasksExecutor.shutdown();
            tasksExecutor.awaitTermination(20, TimeUnit.SECONDS);
            tasksExecutor.shutdownNow();

            // Just want to make sure nothing left behind
            drainQueue();

        } catch (InterruptedException e) {

            // Nothing to do here..
        }
    }

    public void run() {

        try {
            drainQueue();

        } catch (Exception e) {

            // We cant throw anything out, or the task will stop, so just swallow all
        }
    }

    private void addFormattedMessageToQueue(LogMessage message) {

        if (isEnoughDiskSpace()) {
            logsBuffer.enqueue(message.getMessage());
        }
    }

    public void addToQueue(ILoggingEvent message) {

        if (isEnoughDiskSpace()) {
            logsBuffer.enqueue(formatMessage(message).getBytes());
        }
    }

    private boolean isEnoughDiskSpace() {

        boolean shouldEnqueue = false;

        // If we are requested not to check free space
        if (fsPercentThreshold == -1) {
            shouldEnqueue = true;
        }
        else {
            int actualFsPercent = (int) (((double)queueFile.getUsableSpace() / queueFile.getTotalSpace()) * 100);
            if (actualFsPercent >= fsPercentThreshold) {

                System.out.println(String.format("Logz.io: Dropping logs, as FS free usable space on %s is %d percent, and the drop threshold is %d percent",
                        queueDir, actualFsPercent, fsPercentThreshold));
            }
            else {
                shouldEnqueue = true;
            }
        }

        return shouldEnqueue;
    }

    private void drainQueue() {

        // Check if we have something to drain
        if (logsBuffer.size() > 0) {

            List<LogMessage> logsList = new ArrayList<LogMessage>();
            boolean gotLogzioServerError = false;

            while (!logsBuffer.isEmpty() && !gotLogzioServerError) {

                boolean exceededMaxSize = false;

                while (!exceededMaxSize && !logsBuffer.isEmpty()) {

                    logsList.add(new LogMessage(logsBuffer.dequeue()));

                    // Calculate the size
                    if (getSizeOfLogList(logsList) >= MAX_SIZE_IN_BYTES) {

                        exceededMaxSize = true;
                    }
                }

                try {
                    sendToLogzio(logsList);

                } catch (LogzioServerErrorException e) {

                    // We want to quite the loop, and wait the timeout until next iteration
                    gotLogzioServerError = true;

                    // And lets return everything to the queue
                    logsList.forEach(this::addFormattedMessageToQueue);
                }
            }
        }
    }

    private int getSizeOfLogList(List<LogMessage> logsList) {

        int totalSize = 0;
        for (LogMessage currLog : logsList) totalSize += currLog.getSize();

        return totalSize;
    }

    private void sendToLogzio(List<LogMessage> messages) throws LogzioServerErrorException {

        try {

            // Now we need to join all logs, and since we work in byte[] -
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            for (LogMessage currMessage : messages) byteOutputStream.write(currMessage.getMessage());
            byte[] payload = byteOutputStream.toByteArray();

            int currentRetrySleep = INITIAL_WAIT_BEFORE_RETRY;

            for (int currTry = 1; currTry <= MAX_NUMBER_OF_RETRIES; currTry++) {

                boolean shouldRetry = true;

                try {
                    conn = (HttpURLConnection)logzioListener.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-length", String.valueOf(payload.length));
                    conn.setRequestProperty("Content-Type", "text/plain");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    DataOutputStream output = new DataOutputStream(conn.getOutputStream());
                    output.write(payload);
                    output.close();

                    switch (conn.getResponseCode()) {

                        case HttpURLConnection.HTTP_OK:
                        case HttpURLConnection.HTTP_BAD_REQUEST:
                            shouldRetry = false;
                            break;

                        case HttpURLConnection.HTTP_UNAUTHORIZED:
                            shouldRetry = false;
                            System.out.println("Logz.io: Got forbidden! Your token is not right. Unfortunately, dropping logs.");
                            break;
                    }
                }
                catch (IOException e) {

                    // Just swallow, and we should retry
                }

                if (!shouldRetry) {
                    break;

                } else {

                    if (currTry == MAX_NUMBER_OF_RETRIES) {

                        // Giving up, something is broken on Logz.io side, we will try again later
                        throw new LogzioServerErrorException();
                    }

                    Thread.sleep(currentRetrySleep);
                    currentRetrySleep *= 2;
                }
            }

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private String formatMessage(ILoggingEvent iLoggingEvent) {

        Date timeStamp = new Date(iLoggingEvent.getTimeStamp());
        DateTimeFormatter formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.of("UTC"));

        String logMessage = String.format("{\"@timestamp\": \"%s\", \"loglevel\": \"%s\", \"message\": \"%s\", \"logger\": \"%s\", \"thread\": \"%s\"}\n",
                formatter.format(timeStamp.toInstant()), iLoggingEvent.getLevel().levelStr, iLoggingEvent.getFormattedMessage(), iLoggingEvent.getLoggerName(), iLoggingEvent.getThreadName());

        return logMessage;
    }
}
