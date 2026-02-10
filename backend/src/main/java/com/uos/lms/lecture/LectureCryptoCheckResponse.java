package com.uos.lms.lecture;

public record LectureCryptoCheckResponse(
        Long lectureId,
        boolean videoEncryptedAtRest,
        boolean videoDecryptionOk,
        boolean hasThumbnail,
        boolean thumbnailEncryptedAtRest,
        boolean thumbnailDecryptionOk
) {
}
