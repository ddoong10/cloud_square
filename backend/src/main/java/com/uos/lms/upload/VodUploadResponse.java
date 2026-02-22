package com.uos.lms.upload;

public record VodUploadResponse(
        String key,
        String vodUrl,
        String sourceUrl
) {
}
