package com.uos.lms.progress;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureProgressController {

    private final LectureProgressService lectureProgressService;

    @PostMapping("/{lectureId}/heartbeat")
    public HeartbeatResponse heartbeat(
            @PathVariable Long lectureId,
            @RequestBody HeartbeatRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return lectureProgressService.processHeartbeat(userId, lectureId, request);
    }

    @PostMapping("/{lectureId}/complete")
    public HeartbeatResponse complete(@PathVariable Long lectureId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return lectureProgressService.markComplete(userId, lectureId);
    }

    @GetMapping("/{lectureId}/resume")
    public ResumeResponse resume(@PathVariable Long lectureId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return lectureProgressService.getResume(userId, lectureId);
    }
}
