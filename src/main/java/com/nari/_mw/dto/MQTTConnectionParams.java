package com.nari._mw.dto;

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