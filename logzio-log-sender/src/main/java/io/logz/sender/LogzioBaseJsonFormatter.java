package io.logz.sender;

import com.google.common.base.Splitter;
import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by MarinaRazumovsky on 18/12/2016.
 */
public abstract class LogzioBaseJsonFormatter<T> {

    public static final String TIMESTAMP = "@timestamp";
    public static final String LOGLEVEL = "loglevel";
    public static final String MARKER = "marker";
    public static final String MESSAGE = "message";
    public static final String LOGGER = "logger";
    public static final String THREAD = "thread";
    public static final String EXCEPTION = "exception";
    public static final String SOURCE = "source";

    public static final String SOURCE_FILE ="file";
    public static final String SOURCE_LINE_NUMBER = "lineNumber";
    public static final String SOURCE_CLASS ="class";
    public static final String SOURCE_METHOD = "method";


    private final Map<String, String> additionalFieldsMap;

    private final Set<String> reservedFields;

    public LogzioBaseJsonFormatter(String additionalFields, boolean addHostname, ILogzioStatusReporter reporter, Set<String> fieldsSet){
        additionalFieldsMap = new HashMap<>();
        reservedFields = fieldsSet;
        if (additionalFields != null) {
            Splitter.on(';').omitEmptyStrings().withKeyValueSeparator('=').split(additionalFields).forEach((k, v) -> {
                if (reservedFields.contains(k)) {
                    reporter.warning("The field name '" + k + "' defined in additionalFields configuration can't be used since it's a reserved field name. This field will not be added to the outgoing log messages");
                }
                else {
                    String value = getValueFromSystemEnvironmentIfNeeded(v);
                    if (value != null) {
                        additionalFieldsMap.put(k, value);
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
    }

    String formatMessage(T loggingEvent){
        JsonObject logMessage = formatMessageAsJson(loggingEvent);
        additionalFieldsMap.forEach(logMessage::addProperty);
        return logMessage + "\n";
    }

    protected abstract JsonObject formatMessageAsJson(T loggingEvent);

    protected abstract String getLoggerName(T loggingEvent);

    String getValueFromSystemEnvironmentIfNeeded(String value) {
        if (value.startsWith("$")) {
            return System.getenv(value.replace("$", ""));
        }
        return value;
    }
}
