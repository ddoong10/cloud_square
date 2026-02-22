package com.uos.lms.lecture;

public record StreamUrlResponse(
        String url,
        String type,
        String token
) {}
