package com.nari._mw.util;

import com.nari._mw.dto.MQTTConnectionParams;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class MQTTClientWrapper {
    private final MqttClient client;

    public MQTTClientWrapper(MQTTConnectionParams params) {
        try {
            String clientId = "mqtt-client-" + UUID.randomUUID();
            this.client = new MqttClient(params.getHost(), clientId, new MemoryPersistence());
            setupClientCallbacks();
            connectClient(params.getUsername(), params.getPassword());
        } catch (MqttException e) {
            log.error("创建MQTT客户端失败: {}", params.getHost(), e);
            throw new RuntimeException("创建MQTT客户端失败", e);
        }
    }

    private void setupClientCallbacks() {
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("已连接到MQTT代理: {}, 是否重连: {}", serverURI, reconnect);
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.error("MQTT代理连接丢失: {}", client.getServerURI(), cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                log.info("收到消息 - Topic: {}, Content: {}",
                        topic, new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                try {
                    if (token != null && token.getMessage() != null) {
                        log.debug("消息成功投递 - Topic: {}", token.getTopics()[0]);
                    }
                } catch (MqttException e) {
                    log.error("访问已投递消息详情时发生错误", e);
                }
            }
        });
    }

    private void connectClient(String username, String password) throws MqttException {
        if (!client.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(username);
            options.setPassword(password != null ? password.toCharArray() : null);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);
            options.setAutomaticReconnect(true);

            client.connect(options);
            log.info("已连接到MQTT代理: {}", client.getServerURI());
        }
    }

    public CompletableFuture<Void> publishMessage(String topic, String message) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            // 确保已连接
            if (!client.isConnected()) {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setConnectionTimeout(30);
                options.setKeepAliveInterval(60);
                options.setAutomaticReconnect(true);
                client.connect(options);
            }

            // 创建消息
            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);

            // 发布消息
            MqttTopic mqttTopic = client.getTopic(topic);
            MqttDeliveryToken token = mqttTopic.publish(mqttMessage);

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    log.info("消息发布成功 - Topic: {}", topic);
                    future.complete(null);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log.error("发布消息到Topic失败: {}", topic, exception);
                    future.completeExceptionally(exception);
                }
            });

            log.debug("消息已加入发布队列 - Topic: {}", topic);
        } catch (MqttException e) {
            log.error("发布消息失败 - Topic: {}", topic, e);
            future.completeExceptionally(e);
        }

        return future;
    }

    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
                log.info("已断开与MQTT代理的连接: {}", client.getServerURI());
            }
        } catch (MqttException e) {
            log.error("关闭MQTT客户端时出错", e);
        }
    }
}