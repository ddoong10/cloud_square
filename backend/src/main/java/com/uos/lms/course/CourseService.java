package com.uos.lms.course;

import com.uos.lms.lecture.LectureRepository;
import com.uos.lms.user.User;
import com.uos.lms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;

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
    public CourseResponse update(Long courseId, CourseUpdateRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

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
    public void delete(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        courseRepository.delete(course);
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
