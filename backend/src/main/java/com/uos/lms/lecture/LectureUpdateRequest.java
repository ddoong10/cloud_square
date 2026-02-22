package com.uos.lms.lecture;

public record LectureUpdateRequest(
        String title,
        Long courseId,
        String description,
        Integer durationSeconds,
        Integer sortOrder
) {}
