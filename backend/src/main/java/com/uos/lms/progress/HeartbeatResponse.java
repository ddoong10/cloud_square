package com.uos.lms.progress;

public record HeartbeatResponse(
        int watchedSeconds,
        int lastPosition,
        boolean completed,
        boolean lectureJustCompleted
) {
}
