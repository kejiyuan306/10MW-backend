package com.nari._mw.util;

import org.springframework.stereotype.Component;

/**
 * Utility class for building MQTT topics
 */
@Component
public class TopicBuilder {
    private static final String PUBLISH_TOPIC_TEMPLATE = "power/10mw/publish/%s";
    private static final String SUBSCRIBE_TOPIC_TEMPLATE = "power/10mw/subscribe/%s";

    public String buildPublishTopic(String deviceId) {
        return String.format(PUBLISH_TOPIC_TEMPLATE, deviceId);
    }
    public String buildSubscribeTopic(String deviceId) {
        return String.format(SUBSCRIBE_TOPIC_TEMPLATE, deviceId);
    }
}
