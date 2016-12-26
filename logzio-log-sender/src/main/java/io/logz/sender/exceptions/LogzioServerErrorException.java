package io.logz.sender.exceptions;

public class LogzioServerErrorException extends Exception {

    public LogzioServerErrorException() {}

    public LogzioServerErrorException(String message) {
        super(message);
    }
}