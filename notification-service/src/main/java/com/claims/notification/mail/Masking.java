package com.claims.notification.mail;

/** Email addresses are personal data: logs show only enough to tell two apart. */
public final class Masking {
    private Masking() {}

    /** {@code jane.doe@example.com} becomes {@code j***@example.com}. */
    public static String email(String address) {
        if (address == null || address.isBlank()) {
            return "(none)";
        }
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
