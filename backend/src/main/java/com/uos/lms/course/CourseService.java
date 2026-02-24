package com.uos.lms.course;

import com.uos.lms.certificate.CertificateRepository;
import com.uos.lms.enrollment.EnrollmentRepository;
import com.uos.lms.lecture.LectureRepository;
import com.uos.lms.progress.LectureProgressRepository;
import com.uos.lms.user.User;
import com.uos.lms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateRepository certificateRepository;
    private final LectureProgressRepository lectureProgressRepository;

    @Transactional(readOnly = true)
    public List<CourseResponse> listPublished() {
        return courseRepository.findByPublishedTrueOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listAll() {
        return courseRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listByCategory(String category) {
        return courseRepository.findByCategoryAndPublishedTrueOrderByCreatedAtDesc(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> search(String keyword) {
        return courseRepository.findByTitleContainingIgnoreCaseAndPublishedTrue(keyword).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse getById(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        return toResponse(course);
    }

    @Transactional
    public CourseResponse create(CourseCreateRequest request, Long instructorId) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + instructorId));

        Course course = Course.builder()
                .title(request.title().trim())
                .description(request.description())
                .thumbnailUrl(normalizeOptional(request.thumbnailUrl()))
                .instructor(instructor)
                .category(normalizeOptional(request.category()))
                .published(false)
                .passCriteria(request.passCriteria() != null ? request.passCriteria() : 80)
                .build();

        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse update(Long courseId, CourseUpdateRequest request, Long userId, boolean isAdmin) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        if (!isAdmin && (course.getInstructor() == null || !course.getInstructor().getId().equals(userId))) {
            throw new org.springframework.security.access.AccessDeniedException("You can only modify your own courses");
        }

        if (request.title() != null) {
            course.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            course.setDescription(request.description());
        }
        if (request.thumbnailUrl() != null) {
            course.setThumbnailUrl(request.thumbnailUrl().trim());
        }
        if (request.category() != null) {
            course.setCategory(request.category().trim());
        }
        if (request.passCriteria() != null) {
            course.setPassCriteria(request.passCriteria());
        }
        if (request.published() != null) {
            course.setPublished(request.published());
        }

        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public void delete(Long courseId, Long userId, boolean isAdmin) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        if (!isAdmin && (course.getInstructor() == null || !course.getInstructor().getId().equals(userId))) {
            throw new org.springframework.security.access.AccessDeniedException("You can only delete your own courses");
        }

        // Delete related records in correct order to avoid FK violations
        List<Long> lectureIds = lectureRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .map(l -> l.getId())
                .toList();

        if (!lectureIds.isEmpty()) {
            lectureProgressRepository.deleteByLectureIdIn(lectureIds);
            log.info("Deleted lecture progress for {} lectures in course {}", lectureIds.size(), courseId);
        }

        certificateRepository.deleteByCourseId(courseId);
        enrollmentRepository.deleteByCourseId(courseId);
        lectureRepository.deleteByCourseId(courseId);
        courseRepository.delete(course);

        log.info("Deleted course {} and all related records", courseId);
    }

    private CourseResponse toResponse(Course course) {
        long lectureCount = lectureRepository.countByCourseId(course.getId());
        User instructor = course.getInstructor();
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getThumbnailUrl(),
                instructor != null ? instructor.getId() : null,
                instructor != null ? instructor.getName() : null,
                course.getCategory(),
                course.isPublished(),
                course.getPassCriteria(),
                lectureCount,
                course.getCreatedAt()
        );
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
