package com.medislot.common.util;

/** Masks PII before it can reach a log line. */
public final class Masking {

    private Masking() {
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }

    public static String phone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "*".repeat(phone.length() - 2) + phone.substring(phone.length() - 2);
    }

    /** Keeps only the last four characters, e.g. {@code ******1234}. */
    public static String nationalId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.length() <= 4 ? "*".repeat(id.length()) : "******" + id.substring(id.length() - 4);
    }
}
