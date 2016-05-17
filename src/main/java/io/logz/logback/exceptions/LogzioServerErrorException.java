package io.logz.logback.exceptions;

/**
 * Created by roiravhon on 5/9/16.
 */
public class LogzioServerErrorException extends Exception {

    public LogzioServerErrorException() {}

    public LogzioServerErrorException(String message) {
        super(message);
    }
}