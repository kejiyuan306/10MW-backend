package com.nari._mw.service;

import com.alibaba.fastjson.JSON;
import com.nari._mw.dto.DeviceFunctionBlockRequest;
import com.nari._mw.exception.MessageProcessingException;
import com.nari._mw.model.FunctionBlockConfiguration;
import com.nari._mw.util.TopicBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {
    private final MQTTService mqttService;
    private final TopicBuilder topicBuilder;

    /**
     * Process function blocks for a device and publish to MQTT
     * @param request The request containing device ID and function blocks
     * @return CompletableFuture that completes when the message is published
     */
    public CompletableFuture<Void> processFunctionBlocks(DeviceFunctionBlockRequest request) {
        try {
            // Create the wrapper object
            FunctionBlockConfiguration configuration = new FunctionBlockConfiguration();
            configuration.setFunctionBlocks(request.getFunctionBlocks());

            // Serialize function blocks to JSON
            String payload = JSON.toJSONString(configuration);
            log.debug("Serialized function blocks for device {}: {}", request.getDeviceId(), payload);

            // Build the topic for this device
            String topic = topicBuilder.buildDeviceTopic(request.getDeviceId());

            // Publish the message
            return mqttService.publishToTopic(topic, payload);
        } catch (Exception e) {
            log.error("Failed to serialize function blocks", e);
            throw new MessageProcessingException("Failed to serialize function blocks", e);
        }
    }
}