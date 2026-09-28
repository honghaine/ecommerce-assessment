package com.flashsale.common.logging;

/**
 * Masks personal data before it reaches logs or API responses.
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /** {@code john.doe@gmail.com} → {@code j******e@gmail.com}. */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1) + domain;
    }

    /** {@code +84912345678} → {@code +84******678}. */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        if (phone.length() <= 6) {
            return "***";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 6) + phone.substring(phone.length() - 3);
    }

    public static String mask(String identifier) {
        if (identifier == null) {
            return null;
        }
        return identifier.contains("@") ? maskEmail(identifier) : maskPhone(identifier);
    }
}
