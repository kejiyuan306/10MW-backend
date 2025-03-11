package com.nari._mw.pojo.dto.mqtt.connect;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MQTTConnectionParams {
    private String host;
    private String username;
    private String password;
}