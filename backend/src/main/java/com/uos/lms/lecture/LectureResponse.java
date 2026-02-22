package com.uos.lms.lecture;

public record LectureResponse(
        Long id,
        String title,
        String videoUrl,
        String thumbnailUrl,
        Long courseId,
        String description,
        String vodUrl,
        Integer durationSeconds,
        Integer sortOrder,
        String resourceUrls
) {
    public static LectureResponse from(Lecture lecture) {
        return new LectureResponse(
                lecture.getId(),
                lecture.getTitle(),
                lecture.getVideoUrl(),
                lecture.getThumbnailUrl(),
                lecture.getCourse() != null ? lecture.getCourse().getId() : null,
                lecture.getDescription(),
                lecture.getVodUrl(),
                lecture.getDurationSeconds(),
                lecture.getSortOrder(),
                lecture.getResourceUrls()
        );
    }
}
