package com.uos.lms.user;

public record ResidentNumberResponse(
        Long userId,
        String residentNumber,
        boolean encryptedAtRest,
        boolean available
) {
}
