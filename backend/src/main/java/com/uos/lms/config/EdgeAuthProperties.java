package com.uos.lms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.vod.edge-auth")
public class EdgeAuthProperties {

    private boolean enabled = false;
    private String key = "";
    private String tokenName = "token";
    private int durationSeconds = 3600;
}
