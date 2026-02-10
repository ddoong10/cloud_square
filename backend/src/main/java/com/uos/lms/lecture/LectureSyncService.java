package com.uos.lms.lecture;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.uos.lms.common.HashingUtils;
import com.uos.lms.config.StorageProperties;
import com.uos.lms.kms.EnvelopeEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class LectureSyncService {

    private static final String[] VIDEO_EXTENSIONS = {
            ".mp4", ".m3u8", ".mov", ".mkv", ".avi", ".webm", ".wmv", ".mpeg", ".mpg", ".m4v", ".ts"
    };

    private final AmazonS3 amazonS3;
    private final StorageProperties storageProperties;
    private final LectureRepository lectureRepository;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    @Value("${app.static-base-url}")
    private String staticBaseUrl;

    public LectureSyncResponse syncFromObjectStorage() {
        String continuationToken = null;
        int scanned = 0;
        int inserted = 0;

        try {
            do {
                ListObjectsV2Request request = new ListObjectsV2Request()
                        .withBucketName(storageProperties.getBucket())
                        .withPrefix("uploads/")
                        .withContinuationToken(continuationToken);

                ListObjectsV2Result result = amazonS3.listObjectsV2(request);
                for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
                    String key = objectSummary.getKey();
                    if (key == null || key.isBlank() || key.endsWith("/")) {
                        continue;
                    }
                    if (!isVideoObject(key)) {
                        continue;
                    }

                    scanned++;
                    String videoUrl = normalizeBaseUrl() + "/" + key;
                    String videoUrlHash = HashingUtils.sha256Hex(videoUrl);
                    if ((videoUrlHash != null && lectureRepository.existsByVideoUrlHash(videoUrlHash))
                            || lectureRepository.existsByVideoUrl(videoUrl)) {
                        continue;
                    }

                    Lecture lecture = Lecture.builder()
                            .title(buildLectureTitleFromObjectKey(key))
                            .videoUrl(envelopeEncryptionService.encrypt(videoUrl))
                            .videoUrlHash(videoUrlHash)
                            .build();

                    lectureRepository.save(lecture);
                    inserted++;
                }

                continuationToken = result.getNextContinuationToken();
            } while (continuationToken != null);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to sync lectures from object storage", exception);
        }

        return new LectureSyncResponse(scanned, inserted);
    }

    private boolean isVideoObject(String key) {
        String lowerKey = key.toLowerCase();
        for (String extension : VIDEO_EXTENSIONS) {
            if (lowerKey.endsWith(extension)) {
                return true;
            }
        }

        // Fallback: some keys can be extensionless.
        try {
            ObjectMetadata metadata = amazonS3.getObjectMetadata(storageProperties.getBucket(), key);
            String contentType = metadata.getContentType();
            return contentType != null && contentType.toLowerCase().startsWith("video/");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String normalizeBaseUrl() {
        return staticBaseUrl.endsWith("/")
                ? staticBaseUrl.substring(0, staticBaseUrl.length() - 1)
                : staticBaseUrl;
    }

    private String buildLectureTitleFromObjectKey(String key) {
        String fileName = key;
        int slashIndex = key.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < key.length() - 1) {
            fileName = key.substring(slashIndex + 1);
        }

        int uuidSeparator = fileName.indexOf('-');
        String titleSeed = uuidSeparator > 0 ? fileName.substring(uuidSeparator + 1) : fileName;

        int extensionIndex = titleSeed.lastIndexOf('.');
        if (extensionIndex > 0) {
            titleSeed = titleSeed.substring(0, extensionIndex);
        }

        String normalizedTitle = titleSeed.replace('_', ' ').trim();
        if (normalizedTitle.isBlank()) {
            normalizedTitle = "Untitled Lecture";
        }

        if (normalizedTitle.length() > 255) {
            return normalizedTitle.substring(0, 255);
        }
        return normalizedTitle;
    }
}
