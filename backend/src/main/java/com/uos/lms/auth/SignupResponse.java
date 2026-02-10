package com.uos.lms.auth;

import com.uos.lms.user.UserRole;

public record SignupResponse(
        Long id,
        String email,
        UserRole role
) {
}
