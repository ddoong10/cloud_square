package com.uos.lms.enrollment;

import com.uos.lms.course.Course;
import com.uos.lms.course.CourseRepository;
import com.uos.lms.lecture.LectureRepository;
import com.uos.lms.progress.LectureProgressRepository;
import com.uos.lms.user.User;
import com.uos.lms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final LectureProgressRepository lectureProgressRepository;

    @Transactional
    public EnrollmentResponse enroll(Long userId, Long courseId) {
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new IllegalArgumentException("Already enrolled in this course");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        Enrollment enrollment = Enrollment.builder()
                .user(user)
                .course(course)
                .status(EnrollmentStatus.IN_PROGRESS)
                .progressPercent(0)
                .build();

        return toResponse(enrollmentRepository.save(enrollment));
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getProgress(Long userId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("Not enrolled in this course"));
        return toResponse(enrollment);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getMyEnrollments(Long userId) {
        return enrollmentRepository.findByUserIdOrderByEnrolledAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void updateProgress(Long userId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElse(null);
        if (enrollment == null) {
            return;
        }

        long totalLectures = lectureRepository.countByCourseId(courseId);
        if (totalLectures == 0) {
            return;
        }

        List<Long> lectureIds = lectureRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .map(l -> l.getId())
                .toList();

        long completedLectures = lectureProgressRepository.countByUserIdAndLectureIdInAndCompletedTrue(userId, lectureIds);
        int progressPercent = (int) (completedLectures * 100 / totalLectures);
        enrollment.setProgressPercent(progressPercent);

        Course course = enrollment.getCourse();
        if (progressPercent >= course.getPassCriteria() && enrollment.getStatus() == EnrollmentStatus.IN_PROGRESS) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(LocalDateTime.now());
        }

        enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void unenroll(Long userId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("Not enrolled in this course"));
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot unenroll from a completed course");
        }
        List<Long> lectureIds = lectureRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .map(l -> l.getId())
                .toList();
        if (!lectureIds.isEmpty()) {
            lectureProgressRepository.deleteByUserIdAndLectureIdIn(userId, lectureIds);
        }
        enrollmentRepository.delete(enrollment);
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(Long userId, Long courseId) {
        return enrollmentRepository.existsByUserIdAndCourseId(userId, courseId);
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        Course course = enrollment.getCourse();
        return new EnrollmentResponse(
                enrollment.getId(),
                course.getId(),
                course.getTitle(),
                course.getThumbnailUrl(),
                enrollment.getStatus(),
                enrollment.getProgressPercent(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt()
        );
    }
}
