package io.logz.log4j2;

import com.google.gson.JsonObject;
import io.logz.sender.ILogzioStatusReporter;
import io.logz.sender.LogzioBaseJsonFormatter;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;


/**
 * Created by MarinaRazumovsky on 13/12/2016.
 */
public class LogzioLog4j2JsonFormatter extends LogzioBaseJsonFormatter<LogEvent> {


    public LogzioLog4j2JsonFormatter(String additionalFields, boolean addHostname, ILogzioStatusReporter reporter) {
        super(additionalFields,addHostname,reporter, new HashSet<>(Arrays.asList(new String[] {TIMESTAMP,LOGLEVEL, MARKER,
                MESSAGE,LOGGER,THREAD,EXCEPTION,SOURCE,SOURCE_FILE,SOURCE_LINE_NUMBER,SOURCE_CLASS,SOURCE_METHOD})));
    }

    @Override
    protected JsonObject formatMessageAsJson(LogEvent loggingEvent) {
        JsonObject logMessage = formatMessageAsJson(loggingEvent.getTimeMillis(),
                loggingEvent.getLevel().toString(),
                loggingEvent.getMessage().getFormattedMessage(),
                loggingEvent.getLoggerName(),
                loggingEvent.getThreadName(),
                Optional.ofNullable(loggingEvent.getMarker()),
                Optional.ofNullable(loggingEvent.getContextData()), // MDC properties
                Optional.ofNullable(loggingEvent.getThrownProxy()),
                Optional.ofNullable(loggingEvent.getSource()));

        // Return the json, while separating lines with \n
        return logMessage;
    }

    @Override
    public String getLoggerName(LogEvent loggingEvent) {
        return loggingEvent.getLoggerName();
    }

    private JsonObject formatMessageAsJson(long timeStamp,
                                           String logLevelName, String renderedMessage, String loggerName, String threadName, Optional<Marker> marker, Optional<ReadOnlyStringMap> mdcPropertyMap,
                                           Optional<ThrowableProxy> throwableProxy,Optional<StackTraceElement> source) {
        JsonObject logMessage = new JsonObject();
        // Adding MDC first, as I dont want it to collide with any one of the following fields
        if (mdcPropertyMap.isPresent()) {
            mdcPropertyMap.get().toMap().forEach(logMessage::addProperty);
        }
        logMessage.addProperty(TIMESTAMP, new Date(timeStamp).toInstant().toString());
        logMessage.addProperty(LOGLEVEL, logLevelName);
        if (marker.isPresent()) {
            logMessage.addProperty(MARKER, marker.get().toString());
        }
        logMessage.addProperty(MESSAGE, renderedMessage);
        logMessage.addProperty(LOGGER, loggerName);
        logMessage.addProperty(THREAD, threadName);
        if (throwableProxy.isPresent()) {
            logMessage.addProperty(EXCEPTION, throwableProxy.get().getCauseStackTraceAsString() );
        }
        if (source.isPresent()) {
            logMessage.add(SOURCE,formatLocationInfo(source.get()));

        }
        return logMessage;
    }

    private JsonObject formatLocationInfo(StackTraceElement locationInfo) {
        JsonObject locationElement = new JsonObject() ;
        locationElement.addProperty(SOURCE_FILE,locationInfo.getFileName());
        locationElement.addProperty(SOURCE_LINE_NUMBER,locationInfo.getLineNumber());
        locationElement.addProperty(SOURCE_CLASS, locationInfo.getClassName());
        locationElement.addProperty(SOURCE_METHOD,locationInfo.getMethodName());
        return locationElement;
    }







}
