package com.uos.lms.vod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uos.lms.config.VodStationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VodStationClient {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final VodStationProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * VOD Station 카테고리에 파일을 추가하여 인코딩을 트리거한다.
     * 비동기로 실행되어 업로드 응답을 지연시키지 않는다.
     */
    @Async
    public void triggerEncoding(String bucketName, String filePath) {
        if (!props.isEnabled()) {
            log.debug("VOD Station encoding disabled, skipping: {}", filePath);
            return;
        }
        if (isBlank(props.getCategoryId()) || isBlank(props.getAccessKey()) || isBlank(props.getSecretKey())) {
            log.warn("VOD Station configuration incomplete, skipping encoding for: {}", filePath);
            return;
        }

        try {
            String path = "/api/v2/category/" + props.getCategoryId() + "/add-files";
            String body = objectMapper.writeValueAsString(new AddFilesRequest(
                    bucketName,
                    List.of(filePath)
            ));

            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = createSignature("PUT", path, timestamp);

            String baseUrl = props.getBaseUrl().endsWith("/")
                    ? props.getBaseUrl().substring(0, props.getBaseUrl().length() - 1)
                    : props.getBaseUrl();

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("x-ncp-iam-access-key", props.getAccessKey())
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-apigw-signature-v2", signature)
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 400) {
                log.error("VOD Station add-files failed: HTTP {} - {}", response.statusCode(), response.body());
            } else {
                log.info("VOD Station encoding triggered: bucket={}, file={}, response={}", bucketName, filePath, response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("VOD Station add-files interrupted for: {}", filePath, e);
        } catch (IOException e) {
            log.error("VOD Station add-files failed for: {}", filePath, e);
        }
    }

    private String createSignature(String method, String path, String timestamp) {
        String message = method + " " + path + "\n" + timestamp + "\n" + props.getAccessKey();
        try {
            SecretKeySpec signingKey = new SecretKeySpec(
                    props.getSecretKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create VOD Station signature", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record AddFilesRequest(String bucketName, List<String> pathList) {
    }
}
