package com.uos.lms.certificate;

import java.time.LocalDateTime;

public record CertificateResponse(
        Long id,
        String certificateNumber,
        String userName,
        String courseName,
        LocalDateTime issuedAt,
        String pdfUrl,
        String verificationUrl
) {
}
