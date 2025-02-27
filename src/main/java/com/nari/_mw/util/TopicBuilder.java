package com.nari._mw.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class for building MQTT topics
 */
@Component
public class TopicBuilder {
    private static final String DEVICE_TOPIC_TEMPLATE = "power/10mw/device/%s";

    /**
     * Builds a topic for a specific device
     * @param deviceId The device ID
     * @return The complete topic string
     */
    public String buildDeviceTopic(String deviceId) {
        return String.format(DEVICE_TOPIC_TEMPLATE, deviceId);
    }
}
