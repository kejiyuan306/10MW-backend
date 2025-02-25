package com.nari._mw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(MQTTConfig.PREFIX)
public class MQTTConfig {
    public static final String PREFIX = "mqtt";

    private String host;
    private String clientId;
    private String username;
    private String password;
    private boolean cleanSession;
    private String topic;
    private int timeout;
    private int keepAlive;
    private int connectionTimeout;
}