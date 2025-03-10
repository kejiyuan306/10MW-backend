package com.nari._mw.dto;

import com.nari._mw.model.FunctionBlock;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class DeviceFunctionBlockRequest {
    @NotBlank(message = "Device ID cannot be empty")
    private String deviceId;

    @NotEmpty(message = "Function blocks list cannot be empty")
    private List<FunctionBlock> functionBlocks;

    private MQTTConnectionParams mqttConnectionParams;
}