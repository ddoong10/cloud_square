package com.uos.lms.kms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class NcpKmsClient {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_V2 = "x-ncp-apigw-signature-v2";
    private static final String ACCESS_KEY_HEADER = "x-ncp-iam-access-key";
    private static final String TIMESTAMP_HEADER = "x-ncp-apigw-timestamp";

    private final KmsProperties kmsProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public KmsDataKey createCustomKey() {
        ensureEnabled();
        String encodedTag = URLEncoder.encode(kmsProperties.getKeyTag(), StandardCharsets.UTF_8);
        String path = "/kms/v1/keys/" + encodedTag + "/create-custom-key";
        String requestBody = "{\"requestPlainKey\":true,\"bits\":256}";
        JsonNode data;
        try {
            data = post(path, requestBody);
        } catch (IllegalStateException exception) {
            // Compatibility fallback for legacy path naming.
            String legacyPath = "/kms/v1/keys/" + encodedTag + "/createCustomKey";
            data = post(legacyPath, requestBody);
        }

        String kmsCiphertext = requiredTextAny(data, "ciphertext", "cipherText");
        String plaintextBase64 = requiredTextAny(data, "plaintext", "plainText");
        byte[] plainKey = Base64.getDecoder().decode(plaintextBase64);

        return new KmsDataKey(kmsCiphertext, plainKey);
    }

    public byte[] decryptCustomKey(String kmsCiphertext) {
        ensureEnabled();
        String encodedTag = URLEncoder.encode(kmsProperties.getKeyTag(), StandardCharsets.UTF_8);
        String path = "/kms/v1/keys/" + encodedTag + "/decrypt";
        String requestBody = "{\"ciphertext\":\"" + escapeJson(kmsCiphertext) + "\"}";
        JsonNode data = post(path, requestBody);

        String plaintextBase64 = requiredTextAny(data, "plaintext", "plainText");
        return Base64.getDecoder().decode(plaintextBase64);
    }

    private JsonNode post(String path, String requestBody) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = createSignature("POST", path, timestamp, kmsProperties.getAccessKey(), kmsProperties.getSecretKey());
            URI uri = URI.create(normalizeBaseUrl(kmsProperties.getBaseUrl()) + path);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header(ACCESS_KEY_HEADER, kmsProperties.getAccessKey())
                    .header(TIMESTAMP_HEADER, timestamp)
                    .header(SIGNATURE_V2, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = parseJson(response.body());
            if (response.statusCode() >= 400) {
                String message = errorMessage(root);
                throw new IllegalStateException("NCP KMS API failed: HTTP " + response.statusCode() + " - " + message);
            }
            String code = root.path("code").asText("");
            if (!code.isBlank() && !"SUCCESS".equalsIgnoreCase(code)) {
                throw new IllegalStateException("NCP KMS API failed: " + errorMessage(root));
            }

            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                throw new IllegalStateException("NCP KMS API response has no data field");
            }
            return dataNode;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NCP KMS API request failed", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("NCP KMS API request failed", exception);
        }
    }

    private void ensureEnabled() {
        if (!kmsProperties.isEnabled()) {
            throw new IllegalStateException("KMS is disabled");
        }
        if (isBlank(kmsProperties.getAccessKey()) || isBlank(kmsProperties.getSecretKey()) || isBlank(kmsProperties.getKeyTag())) {
            throw new IllegalStateException("KMS configuration is incomplete");
        }
    }

    private String requiredTextAny(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        throw new IllegalStateException("NCP KMS response missing required fields");
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse KMS response JSON", exception);
        }
    }

    private String errorMessage(JsonNode root) {
        String message = root.path("message").asText("");
        if (!message.isBlank()) {
            return message;
        }
        message = root.path("error").path("message").asText("");
        if (!message.isBlank()) {
            return message;
        }
        return "Unknown error";
    }

    private String createSignature(String method, String path, String timestamp, String accessKey, String secretKey) {
        String space = " ";
        String newLine = "\n";
        String message = method + space + path + newLine + timestamp + newLine + accessKey;

        try {
            SecretKeySpec signingKey = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create KMS signature", exception);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record KmsDataKey(String ciphertext, byte[] plainKey) {
    }
}
