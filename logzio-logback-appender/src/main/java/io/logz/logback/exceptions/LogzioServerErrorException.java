package io.logz.logback.exceptions;

public class LogzioServerErrorException extends Exception {

    public LogzioServerErrorException() {}

    public LogzioServerErrorException(String message) {
        super(message);
    }
}