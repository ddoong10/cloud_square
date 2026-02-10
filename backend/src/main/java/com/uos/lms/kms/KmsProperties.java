package com.uos.lms.kms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.kms")
public class KmsProperties {

    private boolean enabled = false;
    private String baseUrl = "https://ocapi.ncloud.com";
    private String keyTag = "";
    private String accessKey = "";
    private String secretKey = "";

    // Reserved for token-based OCAPI auth mode. TODO: wire token flow when needed.
    private String tokenCreatorId = "";
}
