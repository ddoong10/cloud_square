package com.uos.lms.upload;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.uos.lms.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class UploadService {

    private final AmazonS3 amazonS3;
    private final StorageProperties storageProperties;

    @Value("${app.static-base-url}")
    private String staticBaseUrl;

    @Value("${app.vod.cdn-base-url}")
    private String vodCdnBaseUrl;

    @Value("${app.vod.bucket-enc-name}")
    private String vodBucketEncName;

    @Value("${app.vod.input-bucket}")
    private String vodInputBucket;

    public VodUploadResponse uploadVod(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Video file is required");
        }

        String uuid = UUID.randomUUID().toString();
        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        String key = "videos/" + uuid + "/" + safeFilename;

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest request = new PutObjectRequest(
                    vodInputBucket,
                    key,
                    inputStream,
                    metadata
            );

            amazonS3.putObject(request);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to upload VOD file", exception);
        }

        // HLS URL via VOD Station CDN
        String normalizedCdnBase = vodCdnBaseUrl.endsWith("/")
                ? vodCdnBaseUrl.substring(0, vodCdnBaseUrl.length() - 1)
                : vodCdnBaseUrl;
        String vodUrl = normalizedCdnBase + "/hls/" + vodBucketEncName + "/" + key + "/index.m3u8";

        String endpoint = storageProperties.getEndpoint().endsWith("/")
                ? storageProperties.getEndpoint().substring(0, storageProperties.getEndpoint().length() - 1)
                : storageProperties.getEndpoint();
        String sourceUrl = endpoint + "/" + vodInputBucket + "/" + key;

        log.info("VOD uploaded: key={}, vodUrl={}, size={} bytes", key, vodUrl, file.getSize());
        return new VodUploadResponse(key, vodUrl, sourceUrl);
    }

    public UploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        String key = buildObjectKey(file.getOriginalFilename(), file.getContentType());

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest request = new PutObjectRequest(
                    storageProperties.getBucket(),
                    key,
                    inputStream,
                    metadata
            );

            amazonS3.putObject(request);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read upload file", exception);
        }

        String url = buildStaticUrl(key);
        log.info("File uploaded to object storage: key={}, size={} bytes, contentType={}", key, file.getSize(), file.getContentType());

        return new UploadResponse(key, url);
    }

    private String buildObjectKey(String originalFilename, String contentType) {
        String safeFilename = sanitizeFilename(originalFilename);
        LocalDate now = LocalDate.now();
        String prefix = resolvePrefix(safeFilename, contentType);

        return String.format(
                "%s/%d/%02d/%02d/%s-%s",
                prefix,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                UUID.randomUUID(),
                safeFilename
        );
    }

    private String resolvePrefix(String safeFilename, String contentType) {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return "thumbnails";
        }

        String lower = safeFilename.toLowerCase();
        if (lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")) {
            return "thumbnails";
        }
        return "uploads";
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }

        String sanitized = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.length() > 120) {
            return sanitized.substring(sanitized.length() - 120);
        }
        return sanitized;
    }

    private String buildStaticUrl(String key) {
        String normalizedBaseUrl = staticBaseUrl.endsWith("/")
                ? staticBaseUrl.substring(0, staticBaseUrl.length() - 1)
                : staticBaseUrl;
        return normalizedBaseUrl + "/" + key;
    }
}
