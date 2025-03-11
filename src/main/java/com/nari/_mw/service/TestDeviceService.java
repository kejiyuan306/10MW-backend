package com.nari._mw.service;

import com.nari._mw.config.MQTTDefaultConfig;
import com.nari._mw.exception.DeviceInteractionException;
import com.nari._mw.pojo.dto.mqtt.connect.MQTTConnectionParams;
import com.nari._mw.util.MQTTClientWrapper;
import com.nari._mw.util.TopicBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDeviceService {
    private final TopicBuilder topicBuilder;
    private final MQTTDefaultConfig mqttDefaultConfig;
    private final int MAX_TRY_TIME = 50;

    public CompletableFuture<String> test() {
        String deviceId = "device123";
        MQTTConnectionParams params = new MQTTConnectionParams("tcp://localhost:1883", "admin", "abcd1234");

        String publishTopic = topicBuilder.buildPublishTopic(deviceId);
        String subscribeTopic = topicBuilder.buildSubscribeTopic(deviceId);

        MQTTClientWrapper mqttClient = new MQTTClientWrapper(params);

        mqttClient.subscribe(subscribeTopic);
        mqttClient.publishMessage(publishTopic, "hello world");

        return CompletableFuture.supplyAsync(() -> mqttClient.listen(subscribeTopic));
    }

    public CompletableFuture<Void> testInteract() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String deviceId = "device123";
        MQTTConnectionParams params = new MQTTConnectionParams("tcp://localhost:1883", "admin", "abcd1234");

        String publishTopic = topicBuilder.buildPublishTopic(deviceId);
        String subscribeTopic = topicBuilder.buildSubscribeTopic(deviceId);

        MQTTClientWrapper mqttClient = new MQTTClientWrapper(params);

        mqttClient.subscribe(subscribeTopic);


        return CompletableFuture.supplyAsync(() -> {
            String startMsg = "start";
            String endMsg = "end";
            int[] testArr = new int[]{1, 2, 3, 4, 5};
            String expectedMsg = "success";
            // 1. test start
            tryPublish(mqttClient, publishTopic, subscribeTopic, startMsg, deviceId, expectedMsg);
            for (int num : testArr) {
                // 2. test num
                tryPublish(mqttClient, publishTopic, subscribeTopic, String.valueOf(num), deviceId, expectedMsg);
            }
            // 3. test end
            tryPublish(mqttClient, publishTopic, subscribeTopic, endMsg, deviceId, expectedMsg);
            return null;
        });
    }



    private void tryPublish(MQTTClientWrapper mqttClient, String publishTopic, String subscribeTopic,
                            String msg, String expectedMsg, String deviceId) {
        mqttClient.publishMessage(publishTopic, msg);
        String response = mqttClient.listen(subscribeTopic);
        for (int i = 0; i < MAX_TRY_TIME; i++) {
            if (response.equals(expectedMsg)) break;
            mqttClient.publishMessage(publishTopic, msg);
            response = mqttClient.listen(subscribeTopic);
        }
        if (!response.equals(expectedMsg))
            throw new DeviceInteractionException("设备返回错误响应，超出重试次数", deviceId);
    }
}