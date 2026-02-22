package com.uos.lms.security;

import com.uos.lms.config.EdgeAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class EdgeAuthTokenGenerator {

    private final EdgeAuthProperties props;

    /**
     * vodUrl에 Edge Auth 토큰을 추가하여 서명된 URL 반환.
     * Edge Auth가 비활성이면 원본 URL 그대로 반환.
     */
    public String signUrl(String vodUrl) {
        if (!props.isEnabled() || props.getKey() == null || props.getKey().isBlank()) {
            return vodUrl;
        }
        String token = generateToken(vodUrl);
        if (token == null) {
            return vodUrl;
        }
        String separator = vodUrl.contains("?") ? "&" : "?";
        return vodUrl + separator + props.getTokenName() + "=" + token;
    }

    /**
     * vodUrl에 대한 Edge Auth 토큰 문자열만 반환.
     * 프론트엔드에서 HLS 세그먼트 요청에 토큰을 별도로 붙일 수 있도록 제공.
     */
    public String generateToken(String vodUrl) {
        if (!props.isEnabled() || props.getKey() == null || props.getKey().isBlank()) {
            return null;
        }

        String path = extractPath(vodUrl);
        // acl 값 안의 '~'를 '%7E'로 이스케이프 (토큰 구분자 '~'와 충돌 방지)
        String aclPath = (path.substring(0, path.lastIndexOf('/') + 1) + "*").replace("~", "%7E");

        long now = Instant.now().getEpochSecond();
        long exp = now + props.getDurationSeconds();

        String tokenBody = "st=" + now + "~exp=" + exp + "~acl=" + aclPath;
        String hmac = hmacSha256Hex(props.getKey(), tokenBody);
        return tokenBody + "~hmac=" + hmac;
    }

    private String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // NCP Edge Auth 키는 hex 인코딩된 바이너리 키
            byte[] keyBytes = HexFormat.of().parseHex(key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (IllegalArgumentException e) {
            // hex 파싱 실패 시 UTF-8 문자열로 폴백
            try {
                Mac mac2 = Mac.getInstance("HmacSHA256");
                SecretKeySpec keySpec = new SecretKeySpec(
                        key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                mac2.init(keySpec);
                byte[] rawHmac = mac2.doFinal(data.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(rawHmac);
            } catch (Exception ex) {
                throw new IllegalStateException("HMAC-SHA256 computation failed", ex);
            }
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    private String extractPath(String url) {
        try {
            return URI.create(url).getPath();
        } catch (Exception e) {
            // URL 파싱 실패 시 프로토콜+호스트 제거 후 path 추출
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return url;
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) return "/";
            int queryStart = url.indexOf('?', pathStart);
            return queryStart < 0 ? url.substring(pathStart) : url.substring(pathStart, queryStart);
        }
    }
}
