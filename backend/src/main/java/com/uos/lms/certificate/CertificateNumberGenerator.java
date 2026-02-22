package com.uos.lms.certificate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class CertificateNumberGenerator {

    private final CertificateRepository certificateRepository;
    private final AtomicLong counter = new AtomicLong(0);
    private int lastYear = 0;

    public synchronized String generate() {
        int currentYear = Year.now().getValue();
        if (currentYear != lastYear) {
            long existingCount = certificateRepository.count();
            counter.set(existingCount);
            lastYear = currentYear;
        }
        long seq = counter.incrementAndGet();
        return String.format("CERT-%d-%05d", currentYear, seq);
    }
}
