package com.uos.lms.user;

import com.uos.lms.certificate.CertificateRepository;
import com.uos.lms.course.CourseRepository;
import com.uos.lms.enrollment.EnrollmentRepository;
import com.uos.lms.enrollment.EnrollmentStatus;
import com.uos.lms.kms.EnvelopeEncryptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateRepository certificateRepository;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/admin/users")
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @GetMapping("/admin/stats")
    public AdminStatsResponse adminStats() {
        long totalUsers = userRepository.count();
        long totalCourses = courseRepository.count();
        long totalEnrollments = enrollmentRepository.count();
        long totalCertificates = certificateRepository.count();
        long activeStudents = enrollmentRepository.countDistinctUsers();
        long completedEnrollments = enrollmentRepository.countByStatus(EnrollmentStatus.COMPLETED);
        double completionRate = totalEnrollments > 0
                ? Math.round((double) completedEnrollments / totalEnrollments * 1000) / 10.0
                : 0;
        return new AdminStatsResponse(totalUsers, totalCourses, totalEnrollments,
                totalCertificates, activeStudents, completedEnrollments, completionRate);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getEffectiveRole());
    }

    @GetMapping("/me/resident-number")
    public ResidentNumberResponse residentNumber(Authentication authentication) {
        User user = getCurrentUser(authentication);
        String residentEncrypted = user.getResidentNumberEncrypted();
        boolean available = residentEncrypted != null && !residentEncrypted.isBlank();
        boolean encryptedAtRest = envelopeEncryptionService.isEnvelopeValue(residentEncrypted);

        if (!available) {
            return new ResidentNumberResponse(user.getId(), null, encryptedAtRest, false);
        }

        String residentPlain = envelopeEncryptionService.decrypt(residentEncrypted);
        return new ResidentNumberResponse(
                user.getId(),
                formatResidentNumberForDisplay(residentPlain),
                encryptedAtRest,
                true
        );
    }

    @PutMapping("/me/name")
    public MeResponse updateName(@Valid @RequestBody UpdateNameRequest request, Authentication authentication) {
        User user = getCurrentUser(authentication);
        user.setName(request.name().trim());
        userRepository.save(user);
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getEffectiveRole());
    }

    @PutMapping("/me/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        User user = getCurrentUser(authentication);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("현재 비밀번호가 일치하지 않습니다.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadCredentialsException("Unauthorized");
        }

        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Unauthorized"));
    }

    private String formatResidentNumberForDisplay(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("-", "").trim();
        if (normalized.length() == 13) {
            return normalized.substring(0, 6) + "-" + normalized.substring(6);
        }
        return normalized;
    }
}
