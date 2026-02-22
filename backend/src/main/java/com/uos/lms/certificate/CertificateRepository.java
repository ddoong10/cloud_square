package com.uos.lms.certificate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByCertificateNumber(String certificateNumber);

    List<Certificate> findByUserIdOrderByIssuedAtDesc(Long userId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
}
