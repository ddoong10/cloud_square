package com.uos.lms.auth;

import com.uos.lms.user.UserRole;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        UserRole role
) {
}
