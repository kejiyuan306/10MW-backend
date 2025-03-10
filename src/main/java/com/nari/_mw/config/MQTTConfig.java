package com.nari._mw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(MQTTConfig.PREFIX)
public class MQTTConfig {
    public static final String PREFIX = "mqtt";

    private String clientId;
    private String defaultUsername;
    private String defaultPassword;
    private boolean cleanSession = true;
    private int timeout = 30;
    private int keepAlive = 60;
    private int connectionTimeout = 30;
}