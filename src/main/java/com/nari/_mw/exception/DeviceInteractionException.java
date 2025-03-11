package com.nari._mw.exception;

public class DeviceInteractionException extends RuntimeException {
    private final String deviceId;

    public DeviceInteractionException(String message, String deviceId) {
        super(message);
        this.deviceId = deviceId;
    }

    public DeviceInteractionException(String message, String deviceId, Throwable cause) {
        super(message, cause);
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}