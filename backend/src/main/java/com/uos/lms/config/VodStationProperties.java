package com.uos.lms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.vod.station")
public class VodStationProperties {

    private boolean enabled = false;
    private String baseUrl = "https://vodstation.apigw.ntruss.com";
    private String categoryId = "";
    private String accessKey = "";
    private String secretKey = "";
}
