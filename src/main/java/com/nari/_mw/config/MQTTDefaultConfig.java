package com.nari._mw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(MQTTDefaultConfig.PREFIX)
public class MQTTDefaultConfig {
    public static final String PREFIX = "mqtt";

    private String clientId;
    private String defaultUsername;
    private String defaultPassword;
}