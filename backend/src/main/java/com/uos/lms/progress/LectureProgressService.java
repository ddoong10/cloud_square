package com.uos.lms.progress;

import com.uos.lms.enrollment.EnrollmentService;
import com.uos.lms.lecture.Lecture;
import com.uos.lms.lecture.LectureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LectureProgressService {

    private static final int HEARTBEAT_INTERVAL = 30;
    private static final int HEARTBEAT_TOLERANCE = 15;
    private static final double MAX_PLAYBACK_SPEED = 2.0;
    private static final double COMPLETION_THRESHOLD = 0.9;

    private final LectureProgressRepository lectureProgressRepository;
    private final LectureRepository lectureRepository;
    private final EnrollmentService enrollmentService;

    @Transactional
    public HeartbeatResponse processHeartbeat(Long userId, Long lectureId, HeartbeatRequest request) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        LectureProgress progress = lectureProgressRepository.findByUserIdAndLectureId(userId, lectureId)
                .orElseGet(() -> LectureProgress.builder()
                        .userId(userId)
                        .lectureId(lectureId)
                        .watchedSeconds(0)
                        .lastPosition(0)
                        .completed(false)
                        .build());

        if (progress.isCompleted()) {
            progress.setLastPosition(request.currentPosition());
            lectureProgressRepository.save(progress);
            return new HeartbeatResponse(progress.getWatchedSeconds(), progress.getLastPosition(), true, false);
        }

        int increment = calculateIncrement(request, progress);
        progress.setWatchedSeconds(progress.getWatchedSeconds() + increment);
        progress.setLastPosition(request.currentPosition());

        boolean justCompleted = false;
        if (lecture.getDurationSeconds() != null && lecture.getDurationSeconds() > 0) {
            if (progress.getWatchedSeconds() >= (int) (lecture.getDurationSeconds() * COMPLETION_THRESHOLD)) {
                if (!progress.isCompleted()) {
                    progress.setCompleted(true);
                    justCompleted = true;
                }
            }
        }

        lectureProgressRepository.save(progress);

        if (justCompleted && lecture.getCourse() != null) {
            enrollmentService.updateProgress(userId, lecture.getCourse().getId());
        }

        return new HeartbeatResponse(
                progress.getWatchedSeconds(),
                progress.getLastPosition(),
                progress.isCompleted(),
                justCompleted
        );
    }

    @Transactional(readOnly = true)
    public ResumeResponse getResume(Long userId, Long lectureId) {
        LectureProgress progress = lectureProgressRepository.findByUserIdAndLectureId(userId, lectureId)
                .orElse(null);
        if (progress == null) {
            return new ResumeResponse(0, 0, false);
        }
        return new ResumeResponse(progress.getLastPosition(), progress.getWatchedSeconds(), progress.isCompleted());
    }

    @Transactional
    public HeartbeatResponse markComplete(Long userId, Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        LectureProgress progress = lectureProgressRepository.findByUserIdAndLectureId(userId, lectureId)
                .orElseGet(() -> LectureProgress.builder()
                        .userId(userId)
                        .lectureId(lectureId)
                        .watchedSeconds(lecture.getDurationSeconds() != null ? lecture.getDurationSeconds() : 0)
                        .lastPosition(lecture.getDurationSeconds() != null ? lecture.getDurationSeconds() : 0)
                        .completed(false)
                        .build());

        boolean justCompleted = !progress.isCompleted();
        progress.setCompleted(true);
        if (lecture.getDurationSeconds() != null) {
            progress.setWatchedSeconds(lecture.getDurationSeconds());
        }
        lectureProgressRepository.save(progress);

        if (justCompleted && lecture.getCourse() != null) {
            enrollmentService.updateProgress(userId, lecture.getCourse().getId());
        }

        return new HeartbeatResponse(
                progress.getWatchedSeconds(),
                progress.getLastPosition(),
                true,
                justCompleted
        );
    }

    private int calculateIncrement(HeartbeatRequest request, LectureProgress progress) {
        int reportedIncrement = request.watchedSeconds();

        int maxExpected = HEARTBEAT_INTERVAL + HEARTBEAT_TOLERANCE;
        if (reportedIncrement > maxExpected) {
            reportedIncrement = HEARTBEAT_INTERVAL;
        }
        if (reportedIncrement < 0) {
            reportedIncrement = 0;
        }

        if (request.playbackSpeed() > MAX_PLAYBACK_SPEED) {
            log.debug("Playback speed {} exceeds max {}, not counting time for user {}",
                    request.playbackSpeed(), MAX_PLAYBACK_SPEED, progress.getUserId());
            return 0;
        }

        int positionDelta = Math.abs(request.currentPosition() - progress.getLastPosition());
        if (positionDelta < 2 && reportedIncrement > 5) {
            log.debug("Same position repeated, reducing increment for user {}", progress.getUserId());
            return 0;
        }

        return reportedIncrement;
    }
}
