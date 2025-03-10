package com.nari._mw.util;

import com.nari._mw.config.MQTTConfig;
import com.nari._mw.dto.MQTTConnectionParams;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MQTTClientWrapper {
    private final MQTTConfig mqttConfig;
    // 使用连接参数作为键，保存不同连接配置的客户端
    private final Map<String, MqttClient> configuredClients = new ConcurrentHashMap<>();

    public MQTTClientWrapper(MQTTConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
    }

    public CompletableFuture<Void> publishMessage(String brokerUrl, String topic, String message) {
        // 使用默认用户名和密码创建连接参数
        MQTTConnectionParams params = MQTTConnectionParams.builder()
                .host(brokerUrl)
                .username(mqttConfig.getDefaultUsername())
                .password(mqttConfig.getDefaultPassword())
                .build();

        return publishMessage(params, topic, message);
    }

    public CompletableFuture<Void> publishMessage(MQTTConnectionParams params, String topic, String message) {
        try {
            MqttClient client = getOrCreateConfiguredClient(params);
            return publishWithClient(client, topic, message);
        } catch (MqttException e) {
            log.error("发布消息失败 - Broker: {}, Topic: {}", params.getHost(), topic, e);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private MqttClient getOrCreateConfiguredClient(MQTTConnectionParams params) throws MqttException {
        // 创建一个唯一键来标识这个连接配置
        String configKey = params.getHost() + ":" + params.getUsername();

        return configuredClients.computeIfAbsent(configKey, key -> {
            try {
                String clientId = mqttConfig.getClientId() + "-" + UUID.randomUUID();
                MqttClient client = new MqttClient(params.getHost(), clientId, new MemoryPersistence());
                setupClientCallbacks(client, params.getHost());
                connectClient(client, params.getUsername(), params.getPassword());
                return client;
            } catch (MqttException e) {
                log.error("创建配置的MQTT客户端失败: {}", params.getHost(), e);
                throw new RuntimeException("创建配置的MQTT客户端失败", e);
            }
        });
    }

    private void setupClientCallbacks(MqttClient client, String brokerUrl) {
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("已连接到MQTT代理: {}, 是否重连: {}", serverURI, reconnect);
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.error("MQTT代理连接丢失: {}", brokerUrl, cause);
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

    private void connectClient(MqttClient client, String username, String password) throws MqttException {
        if (!client.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(mqttConfig.isCleanSession());
            options.setUserName(username);
            options.setPassword(password != null ? password.toCharArray() : null);
            options.setConnectionTimeout(mqttConfig.getConnectionTimeout());
            options.setKeepAliveInterval(mqttConfig.getKeepAlive());
            options.setAutomaticReconnect(true);

            client.connect(options);
            log.info("已连接到MQTT代理: {}", client.getServerURI());
        }
    }

    private CompletableFuture<Void> publishWithClient(MqttClient client, String topic, String message) throws MqttException {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // 确保已连接
        if (!client.isConnected()) {
            // 使用客户端原有的连接参数重连
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(mqttConfig.isCleanSession());
            options.setConnectionTimeout(mqttConfig.getConnectionTimeout());
            options.setKeepAliveInterval(mqttConfig.getKeepAlive());
            options.setAutomaticReconnect(true);
            client.connect(options);
        }

        // 创建消息
        MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
        mqttMessage.setQos(1);
        mqttMessage.setRetained(false);

        // 获取topic对象并发布
        MqttTopic mqttTopic = client.getTopic(topic);
        MqttDeliveryToken token = mqttTopic.publish(mqttMessage);

        // 设置回调
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
        return future;
    }

    public void disconnect() {
        disconnectClients(configuredClients);
    }

    private void disconnectClients(Map<String, MqttClient> clientMap) {
        clientMap.forEach((key, client) -> {
            try {
                if (client != null && client.isConnected()) {
                    client.disconnect();
                    client.close();
                    log.info("已断开与MQTT代理的连接: {}", client.getServerURI());
                }
            } catch (MqttException e) {
                log.error("关闭MQTT客户端时出错: {}", client.getServerURI(), e);
            }
        });
        clientMap.clear();
    }
}