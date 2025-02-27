package com.nari._mw.service;

import com.nari._mw.config.MQTTConfig;
import com.nari._mw.config.MQTTConnectConfig;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class MQTTService {
    private final MQTTConfig mqttConfig;
    private final MQTTConnectConfig mqttConnectConfig;
    private MqttClient mqttClient;
    private MqttTopic mqttTopic;

    public MQTTService(MQTTConfig mqttConfig, MQTTConnectConfig mqttConnectConfig) {
        this.mqttConfig = mqttConfig;
        this.mqttConnectConfig = mqttConnectConfig;
    }

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(
                    mqttConfig.getHost(),
                    mqttConfig.getClientId(),
                    new MemoryPersistence()
            );
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("Connected to MQTT broker: {}, reconnected: {}", serverURI, reconnect);
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.error("Connection to MQTT broker lost", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    log.info("Message received - Topic: {}, Content: {}",
                            topic, new String(message.getPayload(), StandardCharsets.UTF_8));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    try {
                        if (token != null && token.getMessage() != null) {
                            log.info("Message successfully delivered - Topic: {}, Content: {}",
                                    token.getTopics()[0], new String(token.getMessage().getPayload(), StandardCharsets.UTF_8));
                        } else {
                            log.info("Message delivery complete, but token or message is null");
                        }
                    } catch (MqttException e) {
                        log.error("Error accessing delivered message details", e);
                    }
                }
            });
            connect();
            // Initialize the topic object once connected
            mqttTopic = mqttClient.getTopic(mqttConfig.getTopic());
        } catch (MqttException e) {
            log.error("Failed to initialize MQTT client", e);
            throw new RuntimeException("MQTT initialization failed", e);
        }
    }

    private void connect() throws MqttException {
        if (!mqttClient.isConnected()) {
            mqttClient.connect(mqttConnectConfig.getConnectOptions());
            log.info("Connected to MQTT broker: {}", mqttConfig.getHost());
        }
    }

    public CompletableFuture<Void> publishMessage(String message) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            if (!mqttClient.isConnected()) {
                connect();
                // Re-initialize topic if reconnected
                mqttTopic = mqttClient.getTopic(mqttConfig.getTopic());
            }

            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);

            // Use MqttTopic to publish message and get the token
            MqttDeliveryToken token = mqttTopic.publish(mqttMessage);

            // Add a listener to the token to handle completion
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    try {
                        log.info("Message published successfully - Topic: {}, Content: {}",
                                mqttConfig.getTopic(), message);
                        future.complete(null);
                    } catch (Exception e) {
                        log.error("Error in success callback", e);
                        future.completeExceptionally(e);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log.error("Failed to publish message", exception);
                    future.completeExceptionally(exception);
                }
            });

            log.debug("Message queued for publishing - Topic: {}", mqttConfig.getTopic());
        } catch (MqttException e) {
            log.error("Failed to publish message", e);
            future.completeExceptionally(e);
            throw new RuntimeException("Message publishing failed", e);
        }

        return future;
    }

    /**
     * Publish a message to a specific topic
     * @param topic The topic to publish to
     * @param message The message content
     * @return CompletableFuture that completes when the message is published
     */
    public CompletableFuture<Void> publishToTopic(String topic, String message) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            if (!mqttClient.isConnected()) {
                connect();
            }

            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);

            // Get the topic object for the specified topic
            MqttTopic mqttTopic = mqttClient.getTopic(topic);

            // Publish the message and get the token
            MqttDeliveryToken token = mqttTopic.publish(mqttMessage);

            // Add a listener to the token to handle completion
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    try {
                        log.info("Message published successfully - Topic: {}, Content: {}",
                                topic, message);
                        future.complete(null);
                    } catch (Exception e) {
                        log.error("Error in success callback", e);
                        future.completeExceptionally(e);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log.error("Failed to publish message to topic: {}", topic, exception);
                    future.completeExceptionally(exception);
                }
            });

            log.debug("Message queued for publishing - Topic: {}", topic);
        } catch (MqttException e) {
            log.error("Failed to publish message to topic: {}", topic, e);
            future.completeExceptionally(e);
            throw new RuntimeException("Message publishing failed", e);
        }

        return future;
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("Disconnected from MQTT broker");
            }
        } catch (MqttException e) {
            log.error("Error during MQTT client shutdown", e);
        }
    }
}