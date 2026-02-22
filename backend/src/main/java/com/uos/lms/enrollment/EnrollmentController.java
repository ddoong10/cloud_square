package com.uos.lms.enrollment;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/courses/{courseId}/enroll")
    public EnrollmentResponse enroll(@PathVariable Long courseId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return enrollmentService.enroll(userId, courseId);
    }

    @GetMapping("/api/courses/{courseId}/progress")
    public EnrollmentResponse getProgress(@PathVariable Long courseId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return enrollmentService.getProgress(userId, courseId);
    }

    @GetMapping("/api/my-learning")
    public List<EnrollmentResponse> getMyLearning(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return enrollmentService.getMyEnrollments(userId);
    }
}
