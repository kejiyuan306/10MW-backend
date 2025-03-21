package com.nari._mw.pojo.dto.request;

import com.nari._mw.pojo.dto.mqtt.connect.MQTTConnectionParams;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PublishConfigRequest {
    @NotBlank(message = "Device ID cannot be empty")
    private String deviceId;

    @NotBlank(message = "Config file path cannot be empty")
    private String configFilePath;

    private int sliceSize;

    private MQTTConnectionParams mqttConnectionParams;
}
