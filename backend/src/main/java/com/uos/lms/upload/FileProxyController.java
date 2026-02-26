package com.uos.lms.upload;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.uos.lms.config.StorageProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileProxyController {

    private final AmazonS3 amazonS3;
    private final StorageProperties storageProperties;

    @GetMapping("/**")
    public ResponseEntity<?> proxyFile(HttpServletRequest request) {
        String key = extractKey(request);

        if (key == null || key.isBlank() || key.contains("..")) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "Invalid file path"));
        }

        try {
            S3Object s3Object = amazonS3.getObject(storageProperties.getBucket(), key);
            String contentType = s3Object.getObjectMetadata().getContentType();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                    .contentLength(s3Object.getObjectMetadata().getContentLength())
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                    .body(new InputStreamResource(s3Object.getObjectContent()));
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", "File not found"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "Failed to retrieve file"));
        }
    }

    private String extractKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        String prefix = "/api/files/";
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            return path.substring(prefix.length());
        }
        return null;
    }
}
