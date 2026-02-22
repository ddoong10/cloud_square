package com.uos.lms.progress;

public record ResumeResponse(
        int lastPosition,
        int watchedSeconds,
        boolean completed
) {
}
