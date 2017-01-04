package io.logz.logback;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.gson.JsonObject;

import io.logz.sender.ILogzioStatusReporter;
import io.logz.sender.LogzioBaseJsonFormatter;
import org.slf4j.Marker;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by MarinaRazumovsky on 13/12/2016.
 */
public class LogzioLogbackJsonFormatter extends LogzioBaseJsonFormatter<ILoggingEvent> {

    private final ThrowableProxyConverter throwableProxyConverter;
    private final List<String> throwableProxyConversionOptions = Arrays.asList("full");

    public LogzioLogbackJsonFormatter(String additionalFields, boolean addHostname, ILogzioStatusReporter reporter) {
        super(additionalFields,addHostname,reporter,
                new HashSet<>(Arrays.asList(new String[] {TIMESTAMP,LOGLEVEL, MARKER, MESSAGE,LOGGER,THREAD,EXCEPTION})));
        throwableProxyConverter = new ThrowableProxyConverter();
        throwableProxyConverter.setOptionList(throwableProxyConversionOptions);
        throwableProxyConverter.start();
    }

    @Override
    protected JsonObject formatMessageAsJson(ILoggingEvent loggingEvent) {

        JsonObject logMessage = formatMessageAsJson(loggingEvent.getTimeStamp(), loggingEvent.getLevel().levelStr,
                loggingEvent.getFormattedMessage(), loggingEvent.getLoggerName(), loggingEvent.getThreadName(),
                Optional.ofNullable(loggingEvent.getMarker()), Optional.ofNullable(loggingEvent.getMDCPropertyMap()), Optional.ofNullable(loggingEvent));

        // Return the json, while separating lines with \n
        return logMessage;
    }


    @Override
    public String getLoggerName(ILoggingEvent loggingEvent) {
        return loggingEvent.getLoggerName();
    }

    private JsonObject formatMessageAsJson(long timestamp, String logLevelName, String message, String loggerName, String threadName,
                                           Optional<Marker> marker, Optional<Map<String, String>> mdcPropertyMap, Optional<ILoggingEvent> loggingEvent) {

        JsonObject logMessage = new JsonObject();

        // Adding MDC first, as I dont want it to collide with any one of the following fields
        if (mdcPropertyMap.isPresent()) {
            mdcPropertyMap.get().forEach(logMessage::addProperty);
        }

        logMessage.addProperty(TIMESTAMP, new Date(timestamp).toInstant().toString());
        logMessage.addProperty(LOGLEVEL,logLevelName);

        if (marker.isPresent()) {
            logMessage.addProperty(MARKER, marker.get().toString());
        }

        logMessage.addProperty(MESSAGE, message);
        logMessage.addProperty(LOGGER, loggerName);
        logMessage.addProperty(THREAD, threadName);

        if (loggingEvent.isPresent()) {
            if (loggingEvent.get().getThrowableProxy() != null) {
                logMessage.addProperty(EXCEPTION, throwableProxyConverter.convert(loggingEvent.get()));
            }
        }
        return logMessage;
    }

}
