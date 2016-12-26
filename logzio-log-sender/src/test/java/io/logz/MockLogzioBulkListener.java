package io.logz;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.jetty.io.RuntimeIOException;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MockLogzioBulkListener implements Closeable {
   private final static Logger logger = LoggerFactory.getLogger(MockLogzioBulkListener.class);

    private Server server;
    private Queue<LogRequest> logRequests = new ConcurrentLinkedQueue<>();
    private final String host;
    private final int port;

    private boolean makeServerTimeout = false;
    private boolean raiseExceptionOnLog = false;
    private int timeoutMillis = 10000;

    public void setFailWithServerError(boolean raiseExceptionOnLog) {
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

                logger.debug("got request with query string: {} ({})", request.getQueryString(), this);

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

                        String queryString = request.getQueryString();

                        logRequests.add(new LogRequest(queryString, line));
                            logger.debug("got log: {} ", line);
                        }
                );

                logger.debug("Total number of logRequests {} ({})", logRequests.size(), logRequests);

                // Tell Jetty we are ok, and it should return 200
                baseRequest.setHandled(true);
            }
        });
        logger.debug("Created a mock listener ("+this+")");
    }

    public void start() throws Exception {
        logger.info("Starting MockLogzioBulkListener");
        server.start();
        int attempts = 1;
        while (!server.isRunning()) {
            logger.info("Server has not started yet");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            attempts++;
            if (attempts > 10) {
                throw new RuntimeIOException("Failed to start after multiple attempts");
            }
        }
        logger.info("Started listening on {}:{} ({})", host, port, this);
    }

    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        int attempts = 1;
        while (server.isRunning()) {
            logger.info("Server has not stopped yet");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            attempts++;
            if (attempts > 10) {
                throw new RuntimeIOException("Failed to stop after multiple attempts");
            }
        }
        logger.info("Stopped listening on {}:{} ({})", host, port, this);
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    public Collection<LogRequest> getReceivedMsgs() {
        return Collections.unmodifiableCollection(logRequests);
    }

    public static class LogRequest {
        private final String token;
        private final String type;
        private final JsonObject jsonObject;

        public LogRequest(String queryString, String logLine) {
            Map<String, String> paramToValueMap = Splitter.on('&').withKeyValueSeparator('=').split(queryString);
            if (paramToValueMap.containsKey("token")) {
                token = paramToValueMap.get("token");
            } else {
                throw new IllegalArgumentException("Token not found in query string: "+queryString);
            }

            if (paramToValueMap.containsKey("type")) {
                type = paramToValueMap.get("type");
            } else {
                throw new IllegalArgumentException("Token not found in query string: "+queryString);
            }

            try {
                jsonObject = new JsonParser().parse(logLine).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalArgumentException("Not a valid json received in body of request. logLine = "
                    + logLine, e);
            }
        }

        public String getToken() {
            return token;
        }

        public String getType() {
            return type;
        }

        public JsonObject getJsonObject() {
            return jsonObject;
        }

        public String getMessage() {
            return getStringFieldOrNull("message");
        }

        public String getLogger() {
            return getStringFieldOrNull("logger");
        }

        public String getLogLevel() {
            return getStringFieldOrNull("loglevel");
        }

        public String getHost() {
            return getStringFieldOrNull("hostname");
        }

        public String getStringFieldOrNull(String fieldName) {
            if (jsonObject.get(fieldName) == null) return null;
            return jsonObject.get(fieldName).getAsString();
        }

        @Override
        public String toString() {
            return "[Token = "+token +", type = "+type+"]: " + jsonObject.toString();
        }
    }

    public Optional<LogRequest> getLogByMessageField(String msg) {
        return logRequests.stream()
                .filter(r -> r.getMessage() != null && r.getMessage().equals(msg))
                .findFirst();
    }

    public int getNumberOfReceivedLogs() {
        return logRequests.size();
    }
}