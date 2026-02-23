package com.uos.lms.lecture;

import java.util.List;

public record StreamUrlResponse(
        String url,
        String type,
        String token,
        List<QualityVariant> variants,
        String variantToken
) {
    public record QualityVariant(String url, int bandwidth, String resolution, String name) {}
}
