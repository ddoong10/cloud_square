package com.uos.lms.lecture;

import com.amazonaws.services.s3.AmazonS3;
import com.uos.lms.common.HashingUtils;
import com.uos.lms.config.StorageProperties;
import com.uos.lms.config.VodStationProperties;
import com.uos.lms.course.Course;
import com.uos.lms.course.CourseRepository;
import com.uos.lms.enrollment.EnrollmentRepository;
import com.uos.lms.kms.EnvelopeEncryptionService;
import com.uos.lms.security.EdgeAuthTokenGenerator;
import com.uos.lms.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class LectureService {

    private final LectureRepository lectureRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ObjectProvider<AmazonS3> amazonS3Provider;
    private final ObjectProvider<StorageProperties> storagePropertiesProvider;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final EdgeAuthTokenGenerator edgeAuthTokenGenerator;
    private final VodStationProperties vodStationProperties;

    @Value("${app.static-base-url}")
    private String staticBaseUrl;

    @Value("${app.vod.cdn-base-url}")
    private String vodCdnBaseUrl;

    @Value("${app.vod.bucket-enc-name}")
    private String vodBucketEncName;

    public List<LectureResponse> list() {
        return lectureRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponseWithDecryption)
                .filter(response -> response.videoUrl() != null && !response.videoUrl().endsWith("/sample.mp4"))
                .filter(response -> !isImageUrl(response.videoUrl()))
                .toList();
    }

    public List<LectureResponse> listByCourse(Long courseId) {
        return lectureRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .map(this::toPublicResponse)
                .toList();
    }

    public StreamUrlResponse getStreamUrl(Long lectureId, Long userId, Authentication authentication) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        // Admin and instructor can access any lecture
        boolean isPrivileged = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_INSTRUCTOR"));

        if (!isPrivileged && lecture.getCourse() != null) {
            Long courseId = lecture.getCourse().getId();
            if (!enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
                throw new IllegalStateException("수강 신청 후 시청할 수 있습니다.");
            }
        }

        String vodUrl = lecture.getVodUrl();
        if (vodUrl != null && !vodUrl.isBlank()) {
            String signedUrl = edgeAuthTokenGenerator.signUrl(vodUrl);
            String token = edgeAuthTokenGenerator.generateToken(vodUrl);

            List<StreamUrlResponse.QualityVariant> variants = buildQualityVariants(vodUrl);
            String variantToken = null;
            if (!variants.isEmpty()) {
                String categoryAcl = "/hls/" + vodBucketEncName + "/" + vodStationProperties.getCategoryName() + "/*";
                variantToken = edgeAuthTokenGenerator.generateTokenForAcl(categoryAcl);
            }

            return new StreamUrlResponse(signedUrl, "hls", token, variants, variantToken);
        }

        String videoUrl = envelopeEncryptionService.decrypt(lecture.getVideoUrl());
        return new StreamUrlResponse(videoUrl, "mp4", null, List.of(), null);
    }

    public LectureResponse update(Long lectureId, LectureUpdateRequest request) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        if (request.title() != null && !request.title().isBlank()) {
            lecture.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            lecture.setDescription(request.description());
        }
        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new IllegalArgumentException("Course not found: " + request.courseId()));
            lecture.setCourse(course);
        }
        if (request.durationSeconds() != null) {
            lecture.setDurationSeconds(request.durationSeconds());
        }
        if (request.sortOrder() != null) {
            lecture.setSortOrder(request.sortOrder());
        }

        return toResponseWithDecryption(lectureRepository.save(lecture));
    }

    public LectureResponse create(LectureCreateRequest request) {
        String plainVideoUrl = request.videoUrl().trim();
        String plainThumbnailUrl = normalizeOptionalUrl(request.thumbnailUrl());

        Course course = null;
        if (request.courseId() != null) {
            course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new IllegalArgumentException("Course not found: " + request.courseId()));
        }

        Lecture lecture = Lecture.builder()
                .title(request.title().trim())
                .videoUrl(envelopeEncryptionService.encrypt(plainVideoUrl))
                .videoUrlHash(HashingUtils.sha256Hex(plainVideoUrl))
                .thumbnailUrl(plainThumbnailUrl)
                .thumbnailUrlHash(HashingUtils.sha256Hex(plainThumbnailUrl))
                .course(course)
                .description(request.description())
                .vodUrl(normalizeOptionalUrl(request.vodUrl()))
                .durationSeconds(request.durationSeconds())
                .sortOrder(request.sortOrder())
                .build();

        return toResponseWithDecryption(lectureRepository.save(lecture));
    }

    public LectureDeleteResponse delete(Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        AmazonS3 amazonS3 = amazonS3Provider.getIfAvailable();
        StorageProperties storageProperties = storagePropertiesProvider.getIfAvailable();
        String plainVideoUrl = envelopeEncryptionService.decrypt(lecture.getVideoUrl());
        String plainThumbnailUrl = decryptIfEnvelope(lecture.getThumbnailUrl());

        DeleteAttemptResult videoDeleteResult = deleteObjectIfPossible(amazonS3, storageProperties, lectureId, plainVideoUrl, "video");
        DeleteAttemptResult thumbnailDeleteResult = deleteObjectIfPossible(amazonS3, storageProperties, lectureId, plainThumbnailUrl, "thumbnail");

        lectureRepository.delete(lecture);
        return new LectureDeleteResponse(
                lectureId,
                videoDeleteResult.attempted(),
                videoDeleteResult.deleted(),
                thumbnailDeleteResult.attempted(),
                thumbnailDeleteResult.deleted()
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

    private List<StreamUrlResponse.QualityVariant> buildQualityVariants(String vodUrl) {
        if (!vodStationProperties.isEnabled()
                || vodStationProperties.getCategoryName() == null
                || vodStationProperties.getCategoryName().isBlank()) {
            return List.of();
        }
        try {
            // vodUrl: {cdnBase}/hls/{bucketEncName}/{path}/{filename}.mp4/index.m3u8
            String pathWithoutIndex = vodUrl.replaceAll("/index\\.m3u8$", "");
            int lastSlash = pathWithoutIndex.lastIndexOf('/');
            String mp4Filename = pathWithoutIndex.substring(lastSlash + 1);
            String baseName = mp4Filename.replaceAll("\\.mp4$", "");

            String channelBase = vodCdnBaseUrl.endsWith("/")
                    ? vodCdnBaseUrl.substring(0, vodCdnBaseUrl.length() - 1)
                    : vodCdnBaseUrl;
            String hlsBase = channelBase + "/hls/" + vodBucketEncName + "/"
                    + vodStationProperties.getCategoryName() + "/";

            return List.of(
                    new StreamUrlResponse.QualityVariant(
                            hlsBase + baseName + "_AVC_HD_1Pass_30fps.mp4/index.m3u8",
                            3000000, "1280x720", "HD 720p"),
                    new StreamUrlResponse.QualityVariant(
                            hlsBase + baseName + "_AVC_SD_1Pass_30fps.mp4/index.m3u8",
                            1500000, "854x480", "SD 480p"),
                    new StreamUrlResponse.QualityVariant(
                            hlsBase + baseName + "_AVC_SD_1Pass_30fps_1.mp4/index.m3u8",
                            800000, "640x360", "SD 360p")
            );
        } catch (Exception e) {
            log.warn("Failed to build quality variants from vodUrl: {}", vodUrl, e);
            return List.of();
        }
    }

    public LectureResponse toResponseWithDecryption(Lecture lecture) {
        String decryptedVideoUrl = envelopeEncryptionService.decrypt(lecture.getVideoUrl());
        String decryptedThumbnailUrl = decryptIfEnvelope(lecture.getThumbnailUrl());
        return new LectureResponse(
                lecture.getId(),
                lecture.getTitle(),
                decryptedVideoUrl,
                decryptedThumbnailUrl,
                lecture.getCourse() != null ? lecture.getCourse().getId() : null,
                lecture.getDescription(),
                lecture.getVodUrl(),
                lecture.getDurationSeconds(),
                lecture.getSortOrder(),
                lecture.getResourceUrls()
        );
    }

    private LectureResponse toPublicResponse(Lecture lecture) {
        String decryptedThumbnailUrl = decryptIfEnvelope(lecture.getThumbnailUrl());
        return new LectureResponse(
                lecture.getId(),
                lecture.getTitle(),
                null,
                decryptedThumbnailUrl,
                lecture.getCourse() != null ? lecture.getCourse().getId() : null,
                lecture.getDescription(),
                null,
                lecture.getDurationSeconds(),
                lecture.getSortOrder(),
                lecture.getResourceUrls()
        );
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
