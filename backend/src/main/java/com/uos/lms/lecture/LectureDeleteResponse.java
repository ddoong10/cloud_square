package com.uos.lms.lecture;

public record LectureDeleteResponse(
        Long lectureId,
        boolean objectDeleteAttempted,
        boolean objectDeleted,
        boolean thumbnailDeleteAttempted,
        boolean thumbnailDeleted
) {
}
