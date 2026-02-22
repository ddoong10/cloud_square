package com.uos.lms.user;

public record MeResponse(
        Long id,
        String email,
        String name,
        UserRole role
) {
}
