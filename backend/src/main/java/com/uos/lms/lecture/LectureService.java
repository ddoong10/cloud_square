package com.uos.lms.lecture;

import com.amazonaws.services.s3.AmazonS3;
import com.uos.lms.common.HashingUtils;
import com.uos.lms.config.StorageProperties;
import com.uos.lms.kms.EnvelopeEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class LectureService {

    private final LectureRepository lectureRepository;
    private final ObjectProvider<AmazonS3> amazonS3Provider;
    private final ObjectProvider<StorageProperties> storagePropertiesProvider;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    @Value("${app.static-base-url}")
    private String staticBaseUrl;

    public List<LectureResponse> list() {
        return lectureRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponseWithDecryption)
                .filter(response -> response.videoUrl() != null && !response.videoUrl().endsWith("/sample.mp4"))
                .filter(response -> !isImageUrl(response.videoUrl()))
                .toList();
    }

    public LectureResponse create(LectureCreateRequest request) {
        String plainVideoUrl = request.videoUrl().trim();
        String plainThumbnailUrl = normalizeOptionalUrl(request.thumbnailUrl());
        Lecture lecture = Lecture.builder()
                .title(request.title().trim())
                .videoUrl(envelopeEncryptionService.encrypt(plainVideoUrl))
                .videoUrlHash(HashingUtils.sha256Hex(plainVideoUrl))
                .thumbnailUrl(plainThumbnailUrl)
                .thumbnailUrlHash(HashingUtils.sha256Hex(plainThumbnailUrl))
                .build();

        return toResponseWithDecryption(lectureRepository.save(lecture));
    }

    public LectureDeleteResponse delete(Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        boolean objectDeleteAttempted = false;
        boolean objectDeleted = false;
        boolean thumbnailDeleteAttempted = false;
        boolean thumbnailDeleted = false;

        AmazonS3 amazonS3 = amazonS3Provider.getIfAvailable();
        StorageProperties storageProperties = storagePropertiesProvider.getIfAvailable();
        String plainVideoUrl = envelopeEncryptionService.decrypt(lecture.getVideoUrl());
        String plainThumbnailUrl = decryptIfEnvelope(lecture.getThumbnailUrl());

        DeleteAttemptResult videoDeleteResult = deleteObjectIfPossible(amazonS3, storageProperties, lectureId, plainVideoUrl, "video");
        objectDeleteAttempted = videoDeleteResult.attempted();
        objectDeleted = videoDeleteResult.deleted();

        DeleteAttemptResult thumbnailDeleteResult = deleteObjectIfPossible(
                amazonS3,
                storageProperties,
                lectureId,
                plainThumbnailUrl,
                "thumbnail"
        );
        thumbnailDeleteAttempted = thumbnailDeleteResult.attempted();
        thumbnailDeleted = thumbnailDeleteResult.deleted();

        lectureRepository.delete(lecture);
        return new LectureDeleteResponse(
                lectureId,
                objectDeleteAttempted,
                objectDeleted,
                thumbnailDeleteAttempted,
                thumbnailDeleted
        );
    }

    public LectureCryptoCheckResponse checkCrypto(Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        boolean videoEncryptedAtRest = envelopeEncryptionService.isEnvelopeValue(lecture.getVideoUrl());
        boolean videoDecryptionOk = canDecrypt(lecture.getVideoUrl());

        boolean hasThumbnail = lecture.getThumbnailUrl() != null && !lecture.getThumbnailUrl().isBlank();
        boolean thumbnailEncryptedAtRest = envelopeEncryptionService.isEnvelopeValue(lecture.getThumbnailUrl());
        boolean thumbnailDecryptionOk = !hasThumbnail
                || !thumbnailEncryptedAtRest
                || canDecrypt(lecture.getThumbnailUrl());

        return new LectureCryptoCheckResponse(
                lectureId,
                videoEncryptedAtRest,
                videoDecryptionOk,
                hasThumbnail,
                thumbnailEncryptedAtRest,
                thumbnailDecryptionOk
        );
    }

    public LectureResponse toResponseWithDecryption(Lecture lecture) {
        String decryptedVideoUrl = envelopeEncryptionService.decrypt(lecture.getVideoUrl());
        String decryptedThumbnailUrl = decryptIfEnvelope(lecture.getThumbnailUrl());
        return new LectureResponse(lecture.getId(), lecture.getTitle(), decryptedVideoUrl, decryptedThumbnailUrl);
    }

    private DeleteAttemptResult deleteObjectIfPossible(
            AmazonS3 amazonS3,
            StorageProperties storageProperties,
            Long lectureId,
            String objectUrl,
            String objectType
    ) {
        String objectKey = extractObjectKey(objectUrl);
        if (amazonS3 == null || storageProperties == null || objectKey == null) {
            return new DeleteAttemptResult(false, false);
        }

        try {
            amazonS3.deleteObject(storageProperties.getBucket(), objectKey);
            return new DeleteAttemptResult(true, true);
        } catch (RuntimeException exception) {
            log.warn(
                    "Object delete failed for lectureId={} type={} key={}",
                    lectureId,
                    objectType,
                    objectKey,
                    exception
            );
            return new DeleteAttemptResult(true, false);
        }
    }

    private boolean canDecrypt(String value) {
        try {
            return envelopeEncryptionService.decrypt(value) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String decryptIfEnvelope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!envelopeEncryptionService.isEnvelopeValue(value)) {
            return value;
        }
        return envelopeEncryptionService.decrypt(value);
    }

    private String normalizeOptionalUrl(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractObjectKey(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        String normalizedBaseUrl = normalizeBaseUrl(staticBaseUrl);
        String normalizedVideoUrl = videoUrl.trim();

        String expectedPrefix = normalizedBaseUrl + "/";
        if (normalizedVideoUrl.toLowerCase(Locale.ROOT).startsWith(expectedPrefix.toLowerCase(Locale.ROOT))) {
            String key = normalizedVideoUrl.substring(expectedPrefix.length());
            return key.isBlank() ? null : key;
        }
        return null;
    }

    private boolean isImageUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    private record DeleteAttemptResult(boolean attempted, boolean deleted) {
    }
}
