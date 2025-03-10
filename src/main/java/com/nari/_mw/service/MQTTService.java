package com.nari._mw.service;

import com.nari._mw.config.MQTTConfig;
import com.nari._mw.dto.MQTTConnectionParams;
import com.nari._mw.exception.MessageProcessingException;
import com.nari._mw.util.MQTTClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class MQTTService {
    private final MQTTConfig mqttConfig;
    private final MQTTClientWrapper mqttClientWrapper;

    public MQTTService(MQTTConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
        this.mqttClientWrapper = new MQTTClientWrapper(mqttConfig);
    }

    @PostConstruct
    public void init() {
        log.info("MQTT服务初始化完成");
    }

    /**
     * 向指定MQTT连接发布消息
     *
     * @param connectionParams 包含host、username和password的连接参数
     * @param topic 发布的主题
     * @param message 消息内容
     * @return 消息发布完成的CompletableFuture
     */
    public CompletableFuture<Void> publishMessage(MQTTConnectionParams connectionParams, String topic, String message) {
        try {
            log.debug("准备发布消息 - Broker: {}, Topic: {}, 用户名: {}",
                    connectionParams.getHost(), topic, connectionParams.getUsername());
            return mqttClientWrapper.publishMessage(connectionParams, topic, message);
        } catch (Exception e) {
            log.error("发布消息失败 - Broker: {}, Topic: {}", connectionParams.getHost(), topic, e);
            throw new MessageProcessingException("消息发布失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        mqttClientWrapper.disconnect();
    }
}