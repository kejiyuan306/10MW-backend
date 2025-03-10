package com.nari._mw.service;

import com.alibaba.fastjson.JSON;
import com.nari._mw.config.MQTTDefaultConfig;
import com.nari._mw.dto.DeviceFunctionBlockRequest;
import com.nari._mw.dto.MQTTConnectionParams;
import com.nari._mw.exception.MQTTValidationException;
import com.nari._mw.exception.MessageProcessingException;
import com.nari._mw.model.FunctionBlockConfiguration;
import com.nari._mw.util.MQTTClientWrapper;
import com.nari._mw.util.TopicBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {
    private final TopicBuilder topicBuilder;
    private final MQTTDefaultConfig mqttDefaultConfig;

    /**
     * Process function blocks for a device and publish to MQTT
     * @param request The request containing device ID, function blocks and MQTT connection parameters
     * @return CompletableFuture that completes when the message is published
     */
    public CompletableFuture<Void> processFunctionBlocks(DeviceFunctionBlockRequest request) {
        MQTTClientWrapper mqttClient = null;
        try {
            // 验证MQTT连接参数
            MQTTConnectionParams params = request.getMqttConnectionParams();
            if (params == null) {
                throw new MQTTValidationException("MQTT连接参数不能为空", 400);
            }

            if (params.getHost() == null || params.getHost().isEmpty()) {
                throw new MQTTValidationException("MQTT代理主机不能为空", 400);
            }

            // 处理凭据：如果用户名和密码都为空，使用默认值
            boolean usernameEmpty = params.getUsername() == null || params.getUsername().isEmpty();
            boolean passwordEmpty = params.getPassword() == null || params.getPassword().isEmpty();

            if (usernameEmpty && passwordEmpty) {
                // 两者都为空，使用默认凭据
                log.debug("使用默认用户名和密码连接MQTT代理: {}", params.getHost());
                params.setUsername(mqttDefaultConfig.getDefaultUsername());
                params.setPassword(mqttDefaultConfig.getDefaultPassword());
            } else if (usernameEmpty || passwordEmpty) {
                // 只有一个为空，报错
                throw new MQTTValidationException("用户名和密码必须同时提供或同时为空", 400);
            }

            // Create the wrapper object
            FunctionBlockConfiguration configuration = new FunctionBlockConfiguration();
            configuration.setFunctionBlocks(request.getFunctionBlocks());

            // Serialize function blocks to JSON
            String payload = JSON.toJSONString(configuration);
            log.debug("设备 {} 的功能块序列化结果: {}", request.getDeviceId(), payload);

            // Build the topic for this device
            String topic = topicBuilder.buildPublishTopic(request.getDeviceId());

            // 创建MQTTClientWrapper实例并发布消息
            log.debug("使用凭据连接MQTT代理: {}, 用户名: {}", params.getHost(), params.getUsername());
            mqttClient = new MQTTClientWrapper(params);
            // 只是满足lamda表达式中的变量为final的要求
            MQTTClientWrapper finalMqttClient = mqttClient;
            return mqttClient.publishMessage(topic, payload)
                    .whenComplete((result, ex) -> {
                        // 无论成功还是失败，都断开连接
                        if (finalMqttClient != null) {
                            finalMqttClient.disconnect();
                        }
                    });

        } catch (MQTTValidationException e) {
            log.error("MQTT连接参数验证失败", e);
            // 确保在异常情况下断开连接
            if (mqttClient != null) {
                mqttClient.disconnect();
            }
            throw e;
        } catch (Exception e) {
            log.error("处理功能块失败", e);
            // 确保在异常情况下断开连接
            if (mqttClient != null) {
                mqttClient.disconnect();
            }
            throw new MessageProcessingException("处理功能块失败: " + e.getMessage(), e);
        }
    }
}