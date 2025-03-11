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

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {
    private final TopicBuilder topicBuilder;
    private final MQTTDefaultConfig mqttDefaultConfig;

    private final int MAX_TRY_TIME = 50;
    private final int DEFAULT_SLICE_SIZE = 10 * 1024;
    private final String METADATA_ACTION = "transfer_config";
    private final String FINAL_MSG = "end";

    public CompletableFuture<Void> publishConfig(PublishConfigRequest request) {
        MQTTClientWrapper mqttClient = null;
        try {
            // 验证MQTT连接参数
            MQTTConnectionParams params = request.getMqttConnectionParams();
            params = validateAndProcessMQTTParams(params);

            // 创建MQTTClientWrapper实例并发布消息
            log.debug("使用凭据连接MQTT代理: {}, 用户名: {}", params.getHost(), params.getUsername());
            mqttClient = new MQTTClientWrapper(params);

            mqttClient.subscribe(MqttTopic.CONFIG_TRANSFER_REQUEST_ACK.getTopic());
            mqttClient.subscribe(MqttTopic.FILE_SLICE_ACK.getTopic());
            mqttClient.subscribe(MqttTopic.FILE_VERIFICATION_RESULT.getTopic());

            ConfigTransferAcknowledgeResponse readyStatus = new ConfigTransferAcknowledgeResponse("ready");
            ConfigTransferAcknowledgeResponse successStatus = new ConfigTransferAcknowledgeResponse("success");

            MQTTClientWrapper finalMqttClient = mqttClient;
            return CompletableFuture.supplyAsync(() -> {
                // 切片
                int sliceSize = request.getSliceSize();
                if (sliceSize <= 0) sliceSize = DEFAULT_SLICE_SIZE;
                ConfigTransferData configTransferData = FileSlicerUtil.sliceFile(request.getConfigFilePath(), sliceSize);
                ConfigTransferMetadata metadata = new ConfigTransferMetadata(METADATA_ACTION, configTransferData.getTaskNo(),
                        configTransferData.getFileName(), configTransferData.getSize(), configTransferData.getNumber(),
                        configTransferData.getSliceSize(), configTransferData.getMd5());
                tryPublishVerificationData(finalMqttClient, MqttTopic.CONFIG_TRANSFER_REQUEST.getTopic(),
                        MqttTopic.CONFIG_TRANSFER_REQUEST_ACK.getTopic(), JSON.toJSONString(metadata), readyStatus, request.getDeviceId());
                for (ConfigTransferSlice slice : configTransferData.getSlices()) {
                    tryPublishSliceData(finalMqttClient, MqttTopic.FILE_DATA_SLICE.getTopic(),
                            MqttTopic.FILE_SLICE_ACK.getTopic(), JSON.toJSONString(slice),
                            new ConfigTransferSliceResponse(slice.getTaskNo(), slice.getNumber(), "success"), request.getDeviceId());
                }

                tryPublishVerificationData(finalMqttClient, MqttTopic.FILE_DATA_SLICE.getTopic(),
                        MqttTopic.FILE_VERIFICATION_RESULT.getTopic(), FINAL_MSG, successStatus, request.getDeviceId());


                return null;
            });
        } catch (MQTTValidationException e) {
            log.error("MQTT连接参数验证失败", e);
            // 确保在异常情况下断开连接
            if (mqttClient != null) {
                mqttClient.disconnect();
            }
            throw e;
        }
    }

    private void tryPublishVerificationData(MQTTClientWrapper mqttClient, String publishTopic, String subscribeTopic,
                                            String msg, ConfigTransferAcknowledgeResponse expectedStatus, String deviceId) {
        mqttClient.publishMessage(publishTopic, msg);
        String response = mqttClient.listen(subscribeTopic);
        ConfigTransferAcknowledgeResponse responseObj = JSON.parseObject(response, ConfigTransferAcknowledgeResponse.class);
        for (int i = 0; i < MAX_TRY_TIME; i++) {
            if (responseObj.equals(expectedStatus)) break;
            mqttClient.publishMessage(publishTopic, msg);
            response = mqttClient.listen(subscribeTopic);
        }
        if (!responseObj.equals(expectedStatus))
            throw new DeviceInteractionException("设备返回错误响应，超出重试次数", deviceId);
    }

    private void tryPublishSliceData(MQTTClientWrapper mqttClient, String publishTopic, String subscribeTopic,
                                     String msg, ConfigTransferSliceResponse expectedStatus, String deviceId) {
        mqttClient.publishMessage(publishTopic, msg);
        String response = mqttClient.listen(subscribeTopic);
        ConfigTransferSliceResponse responseObj = JSON.parseObject(response, ConfigTransferSliceResponse.class);
        for (int i = 0; i < MAX_TRY_TIME; i++) {
            if (responseObj.equals(expectedStatus)) break;
            mqttClient.publishMessage(publishTopic, msg);
            response = mqttClient.listen(subscribeTopic);
        }
        if (!responseObj.equals(expectedStatus))
            throw new DeviceInteractionException("设备返回错误响应，超出重试次数", deviceId);
    }

    /**
     * 处理设备的功能块并发布到MQTT
     *
     * @param request 包含设备ID、功能块和MQTT连接参数的请求
     * @return 当消息发布完成时完成的CompletableFuture
     */
    public CompletableFuture<Void> processFunctionBlocks(DeviceFunctionBlockRequest request) {
        MQTTClientWrapper mqttClient = null;
        try {
            // 验证MQTT连接参数
            MQTTConnectionParams params = request.getMqttConnectionParams();
            params = validateAndProcessMQTTParams(params);

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

    /**
     * 验证和处理MQTT连接参数
     *
     * @param params MQTT连接参数
     * @return 处理后的MQTT连接参数
     * @throws MQTTValidationException 如果参数验证失败
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
}