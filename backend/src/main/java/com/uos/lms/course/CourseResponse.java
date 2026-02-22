package com.uos.lms.course;

import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        String title,
        String description,
        String thumbnailUrl,
        Long instructorId,
        String instructorName,
        String category,
        boolean published,
        int passCriteria,
        long lectureCount,
        LocalDateTime createdAt
) {
}
