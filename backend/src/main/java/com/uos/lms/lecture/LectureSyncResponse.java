package com.uos.lms.lecture;

public record LectureSyncResponse(
        int scannedCount,
        int insertedCount
) {
}
