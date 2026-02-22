package com.uos.lms.enrollment;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        Long courseId,
        String courseTitle,
        String courseThumbnailUrl,
        EnrollmentStatus status,
        int progressPercent,
        LocalDateTime enrolledAt,
        LocalDateTime completedAt
) {
}
