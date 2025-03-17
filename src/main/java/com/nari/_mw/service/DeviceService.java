package com.nari._mw.service;

import com.alibaba.fastjson.JSON;
import com.nari._mw._enum.MqttTopic;
import com.nari._mw.config.MQTTDefaultConfig;
import com.nari._mw.pojo.dto.mqtt.transfer.ConfigTransferAcknowledgeResponse;
import com.nari._mw.pojo.dto.mqtt.transfer.ConfigTransferMetadata;
import com.nari._mw.pojo.dto.mqtt.transfer.ConfigTransferSlice;
import com.nari._mw.pojo.dto.mqtt.transfer.ConfigTransferSliceResponse;
import com.nari._mw.pojo.dto.request.DeviceFunctionBlockRequest;
import com.nari._mw.pojo.dto.mqtt.connect.MQTTConnectionParams;
import com.nari._mw.pojo.dto.request.PublishConfigRequest;
import com.nari._mw.exception.DeviceInteractionException;
import com.nari._mw.exception.MQTTValidationException;
import com.nari._mw.exception.MessageProcessingException;
import com.nari._mw.pojo.model.ConfigTransferData;
import com.nari._mw.pojo.model.FunctionBlockConfiguration;
import com.nari._mw.util.FileSlicerUtil;
import com.nari._mw.util.MQTTClientWrapper;
import com.nari._mw.util.TopicBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {
    private final TopicBuilder topicBuilder;
    private final MQTTDefaultConfig mqttDefaultConfig;

    // 常量定义
    private static final int DEFAULT_MAX_TRY_TIME = 2;
    private static final int DEFAULT_SLICE_SIZE = 10 * 1024;
    private static final String METADATA_ACTION = "transfer_config";

    /**
     * 发布配置到设备
     */
    public CompletableFuture<Void> publishConfig(PublishConfigRequest request) {
        MQTTClientWrapper mqttClient = null;
        try {
            // 验证并处理MQTT连接参数
            MQTTConnectionParams params = validateAndProcessMQTTParams(request.getMqttConnectionParams());

            // 创建MQTT客户端并订阅相关主题
            log.debug("连接MQTT代理: {}, 用户: {}", params.getHost(), params.getUsername());
            mqttClient = new MQTTClientWrapper(params);

            String[] topics = {
                    MqttTopic.CONFIG_TRANSFER_REQUEST_ACK.getTopic(),
                    MqttTopic.FILE_SLICE_ACK.getTopic(),
                    MqttTopic.FILE_VERIFICATION_RESULT.getTopic()
            };

            for (String topic : topics) {
                mqttClient.subscribe(topic);
            }

            // 准备状态响应对象
            ConfigTransferAcknowledgeResponse readyStatus = new ConfigTransferAcknowledgeResponse("ready");
            ConfigTransferAcknowledgeResponse successStatus = new ConfigTransferAcknowledgeResponse("success");

            // 使用最终变量以便在lambda表达式中使用
            final MQTTClientWrapper finalMqttClient = mqttClient;

            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 切片文件
                    int sliceSize = request.getSliceSize() <= 0 ? DEFAULT_SLICE_SIZE : request.getSliceSize();
                    ConfigTransferData configData = FileSlicerUtil.sliceFile(request.getConfigFilePath(), sliceSize);
                    String deviceId = request.getDeviceId();

                    // 发送元数据
                    ConfigTransferMetadata metadata = createMetadata(configData);
                    publishWithRetry(finalMqttClient,
                            MqttTopic.CONFIG_TRANSFER_REQUEST.getTopic(),
                            MqttTopic.CONFIG_TRANSFER_REQUEST_ACK.getTopic(),
                            JSON.toJSONString(metadata),
                            readyStatus,
                            deviceId,
                            DEFAULT_MAX_TRY_TIME);

                    // 发送每个文件切片
                    List<ConfigTransferSlice> slices = configData.getSlices();
                    for (int i = 0; i < slices.size() - 1; i++) {
                        ConfigTransferSliceResponse expectedResponse = new ConfigTransferSliceResponse(
                                slices.get(i).getTaskNo(), slices.get(i).getNumber(), "success");

                        publishWithRetry(finalMqttClient,
                                MqttTopic.FILE_DATA_SLICE.getTopic(),
                                MqttTopic.FILE_SLICE_ACK.getTopic(),
                                JSON.toJSONString(slices.get(i)),
                                expectedResponse,
                                deviceId,
                                DEFAULT_MAX_TRY_TIME);
                    }

                    // 发送最终确认消息
                    publishWithRetry(finalMqttClient,
                            MqttTopic.FILE_DATA_SLICE.getTopic(),
                            MqttTopic.FILE_VERIFICATION_RESULT.getTopic(),
                            JSON.toJSONString(slices.get(slices.size() - 1)),
                            successStatus,
                            deviceId,
                            1);

                    return null;
                } finally {
                    finalMqttClient.disconnect();
                }
            });
        } catch (MQTTValidationException e) {
            log.error("MQTT连接参数验证失败", e);
            disconnectIfNotNull(mqttClient);
            throw e;
        } catch (Exception e) {
            log.error("发布配置过程中发生错误", e);
            disconnectIfNotNull(mqttClient);
            throw new MessageProcessingException("发布配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建配置元数据对象
     */
    private ConfigTransferMetadata createMetadata(ConfigTransferData configData) {
        return new ConfigTransferMetadata(
                METADATA_ACTION,
                configData.getTaskNo(),
                configData.getFileName(),
                configData.getSize(),
                configData.getNumber(),
                configData.getSliceSize(),
                configData.getMd5()
        );
    }

    /**
     * 发布确认数据并重试
     */
    private void publishWithRetry(MQTTClientWrapper mqttClient, String publishTopic, String subscribeTopic,
                                  String message, Object expectedStatus, String deviceId, int retryTime) {
        mqttClient.publishMessage(publishTopic, message);
        String response = mqttClient.listen(subscribeTopic);
        Object responseObj;

        if (expectedStatus instanceof ConfigTransferAcknowledgeResponse) {
            responseObj = JSON.parseObject(response, ConfigTransferAcknowledgeResponse.class);
        } else {
            responseObj = JSON.parseObject(response, ConfigTransferSliceResponse.class);
        }

        // 重试逻辑
        for (int i = 0; i < retryTime && !responseObj.equals(expectedStatus); i++) {
            mqttClient.publishMessage(publishTopic, message);
            response = mqttClient.listen(subscribeTopic);

            if (expectedStatus instanceof ConfigTransferAcknowledgeResponse) {
                responseObj = JSON.parseObject(response, ConfigTransferAcknowledgeResponse.class);
            } else {
                responseObj = JSON.parseObject(response, ConfigTransferSliceResponse.class);
            }
        }

        if (!responseObj.equals(expectedStatus)) {
            throw new DeviceInteractionException("设备返回错误响应，超出重试次数", deviceId);
        }
    }

    /**
     * 处理设备的功能块并发布到MQTT
     */
    public CompletableFuture<Void> processFunctionBlocks(DeviceFunctionBlockRequest request) {
        MQTTClientWrapper mqttClient = null;
        try {
            // 验证MQTT连接参数
            MQTTConnectionParams params = validateAndProcessMQTTParams(request.getMqttConnectionParams());

            // 创建功能块配置对象并序列化
            FunctionBlockConfiguration configuration = new FunctionBlockConfiguration();
            configuration.setFunctionBlocks(request.getFunctionBlocks());
            String payload = JSON.toJSONString(configuration);

            log.debug("设备 {} 的功能块序列化结果: {}", request.getDeviceId(), payload);

            // 构建发布主题
            String topic = topicBuilder.buildPublishTopic(request.getDeviceId());

            // 创建MQTT客户端并发布消息
            log.debug("连接MQTT代理: {}, 用户: {}", params.getHost(), params.getUsername());
            mqttClient = new MQTTClientWrapper(params);

            final MQTTClientWrapper finalMqttClient = mqttClient;
            return mqttClient.publishMessage(topic, payload)
                    .whenComplete((result, ex) -> disconnectIfNotNull(finalMqttClient));

        } catch (MQTTValidationException e) {
            log.error("MQTT连接参数验证失败", e);
            disconnectIfNotNull(mqttClient);
            throw e;
        } catch (Exception e) {
            log.error("处理功能块失败", e);
            disconnectIfNotNull(mqttClient);
            throw new MessageProcessingException("处理功能块失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证和处理MQTT连接参数
     */
    private MQTTConnectionParams validateAndProcessMQTTParams(MQTTConnectionParams params) {
        if (params == null) {
            throw new MQTTValidationException("MQTT连接参数不能为空", 400);
        }

        if (params.getHost() == null || params.getHost().isEmpty()) {
            throw new MQTTValidationException("MQTT代理主机不能为空", 400);
        }

        boolean usernameEmpty = params.getUsername() == null || params.getUsername().isEmpty();
        boolean passwordEmpty = params.getPassword() == null || params.getPassword().isEmpty();

        if (usernameEmpty && passwordEmpty) {
            log.debug("使用默认用户名和密码连接MQTT代理: {}", params.getHost());
            params.setUsername(mqttDefaultConfig.getDefaultUsername());
            params.setPassword(mqttDefaultConfig.getDefaultPassword());
        } else if (usernameEmpty || passwordEmpty) {
            throw new MQTTValidationException("用户名和密码必须同时提供或同时为空", 400);
        }

        return params;
    }

    /**
     * 安全断开MQTT连接
     */
    private void disconnectIfNotNull(MQTTClientWrapper mqttClient) {
        if (mqttClient != null) {
            mqttClient.disconnect();
        }
    }
}