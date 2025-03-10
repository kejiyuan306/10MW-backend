package com.nari._mw.exception;

public class MQTTValidationException extends RuntimeException {
    private final int statusCode;

    public MQTTValidationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}