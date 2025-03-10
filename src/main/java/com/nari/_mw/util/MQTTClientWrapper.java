package com.nari._mw.util;

import com.nari._mw.config.MQTTConfig;
import com.nari._mw.config.MQTTConnectConfig;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Wrapper around the MQTT client providing simplified interface and handling
 * for common MQTT operations.
 */
@Slf4j
public class MQTTClientWrapper {
    private final MQTTConfig mqttConfig;
    private final MQTTConnectConfig mqttConnectConfig;
    private MqttClient mqttClient;

    public MQTTClientWrapper(MQTTConfig mqttConfig, MQTTConnectConfig mqttConnectConfig) {
        this.mqttConfig = mqttConfig;
        this.mqttConnectConfig = mqttConnectConfig;
    }

    /**
     * Initializes the MQTT client with callbacks and establishes connection.
     *
     * @throws MqttException if the initialization fails
     */
    public void initialize() throws MqttException {
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
    }

    /**
     * Establishes connection to the MQTT broker if not already connected.
     *
     * @throws MqttException if the connection fails
     */
    private void connect() throws MqttException {
        if (!mqttClient.isConnected()) {
            mqttClient.connect(mqttConnectConfig.getConnectOptions());
            log.info("Connected to MQTT broker: {}", mqttConfig.getHost());
        }
    }

    /**
     * Publishes a message to the specified topic.
     *
     * @param topic The topic to publish to
     * @param message The message content
     * @return CompletableFuture that completes when the message is published
     * @throws MqttException if the publishing fails
     */
    public CompletableFuture<Void> publishMessage(String topic, String message) throws MqttException {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Ensure connected
        if (!mqttClient.isConnected()) {
            connect();
        }

        // Create message
        MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
        mqttMessage.setQos(1);
        mqttMessage.setRetained(false);

        // Get the topic object and publish
        MqttTopic mqttTopic = mqttClient.getTopic(topic);
        MqttDeliveryToken token = mqttTopic.publish(mqttMessage);

        // Set callbacks
        token.setActionCallback(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                log.info("Message published successfully - Topic: {}, Content: {}", topic, message);
                future.complete(null);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                log.error("Failed to publish message to topic: {}", topic, exception);
                future.completeExceptionally(exception);
            }
        });

        log.debug("Message queued for publishing - Topic: {}", topic);
        return future;
    }

    /**
     * Disconnects from the MQTT broker.
     */
    public void disconnect() {
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