package io.logz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

import ch.qos.logback.classic.Level;

public class MockLogzioBulkListener implements Closeable {
    private final static Logger logger = LoggerFactory.getLogger(MockLogzioBulkListener.class);

    private Server server;
    private List<LogRequest> logRequests = new LinkedList<>();
    private final String host;
    private final int port;

    private boolean makeServerTimeout = false;
    private boolean raiseExceptionOnLog = false;
    private int timeoutMillis = 10000;

    public void setRaiseExceptionOnLog(boolean raiseExceptionOnLog) {
        this.raiseExceptionOnLog = raiseExceptionOnLog;
    }
    public void setMakeServerTimeout(boolean makeServerTimeout) {
        this.makeServerTimeout = makeServerTimeout;
    }
    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public MockLogzioBulkListener(String host, int port) {
        this.host = host;
        this.port = port;
        server = new Server(new InetSocketAddress(host, port));
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

                logger.info("got request with query string: {} ", request.getQueryString());

                if (makeServerTimeout) {

                    try {
                        Thread.sleep(timeoutMillis);
                        baseRequest.setHandled(true);
                        return;
                    }
                    catch (InterruptedException e) {
                        // swallow
                    }
                }

                // Bulks are \n delimited, so handling each log separately
                request.getReader().lines().forEach(line -> {

                        if (raiseExceptionOnLog) {
                            throw new RuntimeException();
                        }

                        final String queryString = request.getQueryString();
                        final String body = line;

                        logRequests.add(new LogRequest(queryString, body));
                        logger.info("got log: {} ", body);
                    }
                );

                logger.info("Total number of logRequests {}", logRequests.size());

                // Tell Jetty we are ok, and it should return 200
                baseRequest.setHandled(true);
            }
        });
    }

    public void start() throws Exception {
        logger.info("Starting MockLogzioBulkListener");
        server.start();
        logger.info(this + " started listening on {}:{}", host, port);
    }

    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info(this + " stopped listening on {}:{}", host, port);
    }

    @Override
    public void close() throws IOException {
        stop();
    }



    class LogRequest {
        String queryString;
        String logLine;

        public LogRequest(String queryString, String logLine) {
            this.queryString = queryString;
            this.logLine = logLine;
        }
    }

    public void cleanRequests() {
        logRequests = new LinkedList<>();
    }

    public boolean checkForLogExistence(String token, String type, String loggerName, Level logLevel, String message) {

        for (LogRequest logRequest : logRequests) {

            boolean found = true;

            // First match the path
            if (!logRequest.queryString.equals("token=" + token + "&type=" + type)) {
                found = false;
            }
            else {

                JsonObject jsonObject = new JsonParser().parse(logRequest.logLine).getAsJsonObject();

                // Checking looger loglevel and message. timestamp and thread are ignored, as they can change
                if (!jsonObject.get("logger").getAsString().equals(loggerName)) {
                    found = false;
                }

                if (!jsonObject.get("loglevel").getAsString().equals(logLevel.levelStr)) {
                    found = false;
                }

                if (!jsonObject.get("message").getAsString().equals(message)) {
                    found = false;
                }
            }

            if (found) {

                // No need to check any further, we have found the message
                return true;
            }
        }

        return false;
    }
}