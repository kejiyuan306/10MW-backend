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

@Slf4j
@Service
public class MQTTService {
    private final MQTTConfig mqttConfig;
    private final MQTTConnectConfig mqttConnectConfig;
    private MqttClient mqttClient;

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
            mqttClient.setCallback(new MqttCallback() {

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
                        log.info("Message successfully delivered - Topic: {}, Content: {}",
                                token.getTopics()[0], new String(token.getMessage().getPayload(), StandardCharsets.UTF_8));
                    } catch (MqttException e) {
                        log.error("Error accessing delivered message details", e);
                    }
                }
            });
            connect();
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

    public void publishMessage(String message) {
        try {
            if (!mqttClient.isConnected()) {
                connect();
            }

            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);

            mqttClient.publish(mqttConfig.getTopic(), mqttMessage);
            log.debug("Message queued for publishing - Topic: {}", mqttConfig.getTopic());
        } catch (MqttException e) {
            log.error("Failed to publish message", e);
            throw new RuntimeException("Message publishing failed", e);
        }
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