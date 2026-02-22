package com.uos.lms.certificate;

import java.time.LocalDateTime;

public record CertificateVerifyResponse(
        String certificateNumber,
        String userName,
        String courseName,
        LocalDateTime issuedAt,
        boolean valid
) {
}
