package com.nari._mw.exception;

public class FileSliceException extends RuntimeException {
    public FileSliceException(String message) {
        super(message);
    }

    public FileSliceException(String message, Throwable cause) {
        super(message, cause);
    }
}