package com.nari._mw.util;

import com.nari._mw.pojo.dto.mqtt.connect.MQTTConnectionParams;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MQTTClientWrapper {
    private final MqttClient client;
    private final ConcurrentHashMap<String, BlockingQueue<String>> messageQueues;
    private static final ConcurrentHashMap<String, MQTTClientWrapper> activeClients = new ConcurrentHashMap<>();

    public MQTTClientWrapper(MQTTConnectionParams params) {
        try {
            String clientId = "mqtt-client-" + UUID.randomUUID();

            // 检查是否有旧连接并关闭
            String serverURI = params.getHost();
            MQTTClientWrapper oldClient = activeClients.get(serverURI);
            if (oldClient != null) {
                oldClient.disconnect();
            }

            this.client = new MqttClient(params.getHost(), clientId, new MemoryPersistence());
            this.messageQueues = new ConcurrentHashMap<>();
            setupClientCallbacks();
            connectClient(params.getUsername(), params.getPassword());

            // 注册当前客户端
            activeClients.put(serverURI, this);
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
                // 重新订阅所有主题
                if (reconnect) {
                    messageQueues.keySet().forEach(topic -> {
                        try {
                            doSubscribe(topic);
                        } catch (MqttException e) {
                            log.error("重连后重新订阅主题失败: {}", topic, e);
                        }
                    });
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.error("MQTT代理连接丢失: {}", client.getServerURI(), cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String messageContent = new String(message.getPayload(), StandardCharsets.UTF_8);
                log.info("收到消息 - Topic: {}, Content: {}", topic, messageContent);

                // 将消息放入对应的队列
                BlockingQueue<String> queue = messageQueues.get(topic);
                if (queue != null) {
                    try {
                        queue.put(messageContent);
                    } catch (InterruptedException e) {
                        log.error("向队列添加消息时被中断: {}", topic, e);
                        Thread.currentThread().interrupt();
                    }
                }
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

    /**
     * 订阅指定的主题
     *
     * @param topic 要订阅的主题
     * @return 订阅成功的CompletableFuture
     */
    public CompletableFuture<Void> subscribe(String topic) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            // 确保客户端已连接
            if (!client.isConnected()) {
                throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
            }

            // 为主题创建队列
            messageQueues.putIfAbsent(topic, new LinkedBlockingQueue<>());

            doSubscribe(topic);
            future.complete(null);
            log.info("成功订阅主题: {}", topic);
        } catch (MqttException e) {
            log.error("订阅主题失败: {}", topic, e);
            future.completeExceptionally(e);
        }
        return future;
    }

    private void doSubscribe(String topic) throws MqttException {
        client.subscribe(topic);
    }

    /**
     * 监听指定主题的消息，直到收到消息或超时
     *
     * @param topic   要监听的主题
     * @param timeout 超时时间（毫秒）
     * @return 收到的消息内容
     * @throws InterruptedException 如果线程被中断
     * @throws RuntimeException     如果超时或该主题没有订阅
     */
    public String listen(String topic, long timeout) throws InterruptedException {
        BlockingQueue<String> queue = messageQueues.get(topic);
        if (queue == null) {
            throw new RuntimeException("未订阅该主题: " + topic);
        }

        String message = queue.poll(timeout, TimeUnit.MILLISECONDS);
        if (message == null) {
            throw new RuntimeException("监听主题超时: " + topic);
        }

        return message;
    }

    /**
     * 监听指定主题的消息，无限期等待
     *
     * @param topic 要监听的主题
     * @return CompletableFuture that completes with the received message content
     */
    public String listen(String topic) {
        BlockingQueue<String> queue = messageQueues.get(topic);
        String message = null;
        try {
            message = queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return message;
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
                // 从活动客户端映射中移除
                activeClients.remove(client.getServerURI());

                client.disconnect();
                client.close();
                log.info("已断开与MQTT代理的连接: {}", client.getServerURI());
            }
        } catch (MqttException e) {
            log.error("关闭MQTT客户端时出错", e);
        }
    }
}