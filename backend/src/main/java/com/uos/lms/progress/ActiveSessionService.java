package com.uos.lms.progress;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveSessionService {

    private static final long SESSION_TIMEOUT_SECONDS = 60;

    private final ConcurrentHashMap<String, Instant> activeSessions = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long userId, Long lectureId, String sessionId) {
        String key = userId + ":" + lectureId;
        Instant now = Instant.now();

        Instant existing = activeSessions.get(key);
        if (existing != null && !isExpired(existing, now)) {
            String existingSession = getSessionKey(userId);
            if (existingSession != null && !existingSession.equals(sessionId)) {
                return false;
            }
        }

        activeSessions.put(key, now);
        activeSessions.put("session:" + userId, now);
        return true;
    }

    public void release(Long userId, Long lectureId) {
        String key = userId + ":" + lectureId;
        activeSessions.remove(key);
    }

    public void heartbeat(Long userId, Long lectureId) {
        String key = userId + ":" + lectureId;
        activeSessions.put(key, Instant.now());
    }

    private boolean isExpired(Instant timestamp, Instant now) {
        return now.minusSeconds(SESSION_TIMEOUT_SECONDS).isAfter(timestamp);
    }

    private String getSessionKey(Long userId) {
        return "session:" + userId;
    }
}
