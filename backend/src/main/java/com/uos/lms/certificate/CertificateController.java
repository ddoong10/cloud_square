package com.uos.lms.certificate;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @PostMapping("/api/courses/{courseId}/certificate")
    public CertificateResponse issueCertificate(@PathVariable Long courseId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return certificateService.issueCertificate(userId, courseId);
    }

    @GetMapping("/api/certificates/{certificateNumber}")
    public CertificateResponse getCertificate(@PathVariable String certificateNumber) {
        return certificateService.getCertificate(certificateNumber);
    }

    @GetMapping("/api/certificates/{certificateNumber}/verify")
    public CertificateVerifyResponse verifyCertificate(@PathVariable String certificateNumber) {
        return certificateService.verifyCertificate(certificateNumber);
    }

    @GetMapping("/api/certificates/{certificateNumber}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String certificateNumber) throws IOException {
        byte[] pdfBytes = certificateService.generatePdf(certificateNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + certificateNumber + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/api/my-certificates")
    public List<CertificateResponse> getMyCertificates(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return certificateService.getMyCertificates(userId);
    }
}
