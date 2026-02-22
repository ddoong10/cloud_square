package com.uos.lms.user;

public record AdminStatsResponse(
        long totalUsers,
        long totalCourses,
        long totalEnrollments,
        long totalCertificates,
        long activeStudents,
        long completedEnrollments,
        double completionRate
) {}
