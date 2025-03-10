package com.nari._mw.service;

import com.nari._mw.config.MQTTConfig;
import com.nari._mw.config.MQTTConnectConfig;
import com.nari._mw.exception.MessageProcessingException;
import com.nari._mw.util.MQTTClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling MQTT communication.
 * Delegates actual MQTT operations to a specialized client wrapper.
 */
@Slf4j
@Service
public class MQTTService {
    private final MQTTConfig mqttConfig;
    private final MQTTClientWrapper mqttClientWrapper;

    public MQTTService(MQTTConfig mqttConfig, MQTTConnectConfig mqttConnectConfig) {
        this.mqttConfig = mqttConfig;
        this.mqttClientWrapper = new MQTTClientWrapper(mqttConfig, mqttConnectConfig);
    }

    @PostConstruct
    public void init() {
        try {
            mqttClientWrapper.initialize();
        } catch (Exception e) {
            log.error("Failed to initialize MQTT client", e);
            throw new MessageProcessingException("MQTT initialization failed", e);
        }
    }

    /**
     * Publishes a message to the default topic configured in MQTTConfig
     *
     * @param message The message content to publish
     * @return CompletableFuture that completes when the message is published
     */
    public CompletableFuture<Void> publishMessage(String message) {
        try {
            return mqttClientWrapper.publishMessage(mqttConfig.getTopic(), message);
        } catch (Exception e) {
            log.error("Failed to publish message", e);
            throw new MessageProcessingException("Message publishing failed", e);
        }
    }

    /**
     * Publish a message to a specific topic
     *
     * @param topic The topic to publish to
     * @param message The message content
     * @return CompletableFuture that completes when the message is published
     */
    public CompletableFuture<Void> publishToTopic(String topic, String message) {
        try {
            return mqttClientWrapper.publishMessage(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish message to topic: {}", topic, e);
            throw new MessageProcessingException("Message publishing failed", e);
        }
    }

    @PreDestroy
    public void destroy() {
        mqttClientWrapper.disconnect();
    }
}