package com.uos.lms.common;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String localPart = parts[0];
        if (localPart.length() <= 2) {
            return "***@" + parts[1];
        }
        String maskedLocal = localPart.substring(0, 2) + "***";
        return maskedLocal + "@" + parts[1];
    }
}
