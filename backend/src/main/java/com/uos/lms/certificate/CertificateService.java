package com.uos.lms.certificate;

import com.uos.lms.course.Course;
import com.uos.lms.course.CourseRepository;
import com.uos.lms.enrollment.Enrollment;
import com.uos.lms.enrollment.EnrollmentRepository;
import com.uos.lms.enrollment.EnrollmentStatus;
import com.uos.lms.user.User;
import com.uos.lms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateNumberGenerator certificateNumberGenerator;
    private final CertificatePdfGenerator certificatePdfGenerator;

    @Value("${app.certificate.verification-base-url:https://lms.uoscholar-server.store/#/verify}")
    private String verificationBaseUrl;

    @Transactional
    public CertificateResponse issueCertificate(Long userId, Long courseId) {
        if (certificateRepository.existsByUserIdAndCourseId(userId, courseId)) {
            Certificate existing = certificateRepository.findByUserIdOrderByIssuedAtDesc(userId).stream()
                    .filter(c -> c.getCourse().getId().equals(courseId))
                    .findFirst()
                    .orElseThrow();
            return toResponse(existing);
        }

        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("Not enrolled in this course"));

        if (enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Course not yet completed. Current progress: " + enrollment.getProgressPercent() + "%");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        String certNumber = certificateNumberGenerator.generate();
        String verificationUrl = verificationBaseUrl + "/" + certNumber;

        Certificate certificate = Certificate.builder()
                .user(user)
                .course(course)
                .certificateNumber(certNumber)
                .verificationUrl(verificationUrl)
                .build();

        certificate = certificateRepository.save(certificate);

        return toResponse(certificate);
    }

    @Transactional(readOnly = true)
    public CertificateResponse getCertificate(String certificateNumber) {
        Certificate certificate = certificateRepository.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateNumber));
        return toResponse(certificate);
    }

    @Transactional(readOnly = true)
    public CertificateVerifyResponse verifyCertificate(String certificateNumber) {
        Certificate certificate = certificateRepository.findByCertificateNumber(certificateNumber)
                .orElse(null);
        if (certificate == null) {
            return new CertificateVerifyResponse(certificateNumber, null, null, null, false);
        }
        return new CertificateVerifyResponse(
                certificate.getCertificateNumber(),
                certificate.getUser().getName(),
                certificate.getCourse().getTitle(),
                certificate.getIssuedAt(),
                true
        );
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> getMyCertificates(Long userId) {
        return certificateRepository.findByUserIdOrderByIssuedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(String certificateNumber) throws IOException {
        Certificate certificate = certificateRepository.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateNumber));

        return certificatePdfGenerator.generate(
                certificate.getUser().getName(),
                certificate.getCourse().getTitle(),
                certificate.getIssuedAt(),
                certificate.getCertificateNumber(),
                certificate.getVerificationUrl()
        );
    }

    private CertificateResponse toResponse(Certificate certificate) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getCertificateNumber(),
                certificate.getUser().getName(),
                certificate.getCourse().getTitle(),
                certificate.getIssuedAt(),
                certificate.getPdfUrl(),
                certificate.getVerificationUrl()
        );
    }
}
