package io.logz;

import com.google.gson.JsonObject;
import io.logz.sender.ILogzioStatusReporter;
import io.logz.sender.LogzioBaseJsonFormatter;

import java.util.Collections;
import java.util.Set;

/**
 * Created by MarinaRazumovsky on 26/12/2016.
 */
public class SimpleTestMessage {

    private String message;
    private long timestamp;
    private String loggerName;


    public SimpleTestMessage(String message, String loggerName, long timestamp) {
        this.message = message;
        this.loggerName = loggerName;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    public static class TestLogzioMessageFormatter extends LogzioBaseJsonFormatter<SimpleTestMessage> {

        public TestLogzioMessageFormatter(String additionalFields, boolean addHostname, ILogzioStatusReporter reporter, Set fieldsSet) {
            super(additionalFields, addHostname, reporter, Collections.emptySet());
        }

        @Override
        protected JsonObject formatMessageAsJson(SimpleTestMessage loggingEvent) {
            JsonObject obj = new JsonObject();
            obj.addProperty(LogzioBaseJsonFormatter.TIMESTAMP,loggingEvent.getTimestamp());
            obj.addProperty(LogzioBaseJsonFormatter.MESSAGE, loggingEvent.getMessage());
            obj.addProperty(LogzioBaseJsonFormatter.LOGGER, loggingEvent.getLoggerName());

            return obj;
        }

        @Override
        protected String getLoggerName(SimpleTestMessage loggingEvent) {
            return loggingEvent.getLoggerName();
        }


    }
}
