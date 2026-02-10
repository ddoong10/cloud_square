package com.uos.lms.lecture;

public record LectureResponse(
        Long id,
        String title,
        String videoUrl,
        String thumbnailUrl
) {
    public static LectureResponse from(Lecture lecture) {
        return new LectureResponse(
                lecture.getId(),
                lecture.getTitle(),
                lecture.getVideoUrl(),
                lecture.getThumbnailUrl()
        );
    }
}
