package io.logz.src;

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

/**
 * Created by roiravhon on 5/16/16.
 */

public class MockHttpServer implements Closeable {
    private final static Logger logger = LoggerFactory.getLogger(MockHttpServer.class);

    private Server server;
    private List<RequestRecord> requestRecords = new LinkedList<>();
    private final String host;
    private final int port;

    public MockHttpServer(String host, int port) {
        this.host = host;
        this.port = port;
        server = new Server(new InetSocketAddress(host, port));
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

                logger.info("got request with query string: {} ", request.getQueryString());

                StringBuffer bodySb = new StringBuffer();
                request.getReader().lines().forEach(line -> {

                            // add a request record
                            final String method = request.getMethod();
                            final String queryString = request.getQueryString();
                            final String body = line;

                            requestRecords.add(new RequestRecord(queryString, body));
                            logger.info("got logline: {} ", body);
                        }
                );

                logger.info("Total number of requestRecords {}", requestRecords.size());
            }
        });
    }

    public void start() throws Exception {
        logger.info("Starting MockHttpServer");
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

    class RequestRecord {
        String queryString;
        String body;

        public RequestRecord(String queryString, String body) {
            this.queryString = queryString;
            this.body = body;
        }
    }

    public void cleanRequests() {
        requestRecords = new LinkedList<>();
    }

    public boolean checkForLogExistance(String token, String type, String loggerName, Level logLevel, String message) {

        for (RequestRecord requestRecord : requestRecords) {

            boolean found = true;

            // First match the path
            if (!requestRecord.queryString.equals("token=" + token + "&type=" + type)) {
                found = false;
            }
            else {

                JsonObject jsonObject = new JsonParser().parse(requestRecord.body).getAsJsonObject();

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