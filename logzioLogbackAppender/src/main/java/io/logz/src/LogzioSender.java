package io.logz.src;

import com.bluejeans.common.bigqueue.BigQueue;
import io.logz.src.exceptions.LogzioServerErrorException;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.UnexpectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by roiravhon on 5/9/16.
 */
public class LogzioSender {

    private final String QUEUE_DIR = System.getProperty("java.io.tmpdir") + "/logzio-logback-buffer";
    private final int MAX_SIZE_IN_BYTES = 3145728;  // 3 MB
    private final String LOGZIO_URL = "https://listener.logz.io:8071";
    private final int INITIAL_WAIT_BEFORE_RETRY = 2000;
    private final int MAX_NUMBER_OF_RETRIES = 3;

    private final ScheduledThreadPoolExecutor tasksExecutor;

    private final BigQueue logsBuffer = new BigQueue(QUEUE_DIR, "logzio-logback-appender");
    private final File queueFile = new File(QUEUE_DIR);

    private URL logzioListener;
    private HttpsURLConnection conn;

    private String logzioToken;
    private int drainTimeout;
    private int fsPercentThreshold;

    public LogzioSender(String logzioToken, int drainTimeout, int fsPercentThreshold) throws UnexpectedException {

        try {
            this.logzioToken = logzioToken;
            this.drainTimeout = drainTimeout;
            this.fsPercentThreshold = fsPercentThreshold;
            logzioListener = new URL(LOGZIO_URL);
            initializeHttpConnection();

        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new UnexpectedException("For some reason could not initialize URL. Cant recover..");
        } finally {

            tasksExecutor = new ScheduledThreadPoolExecutor(1);
        }
    }

    public void start() {

        tasksExecutor.scheduleWithFixedDelay(() -> run(), 0, drainTimeout, TimeUnit.SECONDS);
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

    private void addFormattedMessageToQueue(String message) {

        if (isEnoughDiskSpace()) {
            logsBuffer.enqueue(message.getBytes());
        }
    }

    public void addToQueue(Object message) {

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
            int actualFsPercent = (int) ((queueFile.getUsableSpace() / queueFile.getTotalSpace()) * 100);
            if (actualFsPercent >= fsPercentThreshold) {

                System.out.println(String.format("Logz.io: Dropping logs, as FS free usable space on %s is %d percent, and the drop threshold is %d percent",
                        QUEUE_DIR, actualFsPercent, fsPercentThreshold));
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

            List<String> logsList = new ArrayList<String>();
            boolean gotLogzioServerError = false;

            while (!logsBuffer.isEmpty() && !gotLogzioServerError) {

                boolean exceededMaxSize = false;

                while (!exceededMaxSize && !logsBuffer.isEmpty()) {

                    logsList.add(new String(logsBuffer.dequeue()));

                    // Calculate the size
                    if (SizeMeasurer.getSizeOfStringList(logsList) >= MAX_SIZE_IN_BYTES) {

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

    private void initializeHttpConnection() {

        try {
            conn = (HttpsURLConnection)logzioListener.openConnection();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendToLogzio(List<String> messages) throws LogzioServerErrorException {

        try {

            String payload = String.join("\n", messages);
            int currentRetrySleep = INITIAL_WAIT_BEFORE_RETRY;

            for (int currTry = 1; currTry <= MAX_NUMBER_OF_RETRIES; currTry++) {

                boolean shouldRetry = true;

                try {
                    conn.setRequestMethod("POST");

                    conn.setRequestProperty("Content-length", String.valueOf(payload.length()));
                    conn.setRequestProperty("Content-Type", "text/plain");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    DataOutputStream output = new DataOutputStream(conn.getOutputStream());
                    output.writeBytes(payload);

                    output.close();

                    switch (conn.getResponseCode()) {

                        case HttpURLConnection.HTTP_OK:
                        case HttpURLConnection.HTTP_BAD_REQUEST:
                            shouldRetry = false;
                            break;

                        case HttpURLConnection.HTTP_FORBIDDEN:
                            shouldRetry = false;
                            System.out.println("Logz.io: Got forbidden! Your token is not right. Unfortunately, dropping logs.");
                            break;
                    }
                }
                catch (IOException e) {

                    // The connection got closed, reinitializing it
                    initializeHttpConnection();
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

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String formatMessage(Object o) {

        // TODO
        return "";
    }
}
