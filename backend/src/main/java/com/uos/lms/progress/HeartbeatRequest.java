package com.uos.lms.progress;

public record HeartbeatRequest(
        int currentPosition,
        int watchedSeconds,
        double playbackSpeed
) {
}
