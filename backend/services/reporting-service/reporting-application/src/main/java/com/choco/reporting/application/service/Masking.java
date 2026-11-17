package com.choco.reporting.application.service;

/**
 * Masks a customer's contact details before they appear in a report or an export.
 *
 * <p>An export is a file that leaves the platform and is easy to copy onward, so it carries the least
 * personal data that still answers the seller's question. A report row needs to identify a customer,
 * not to hand over a reachable address: {@code a***e@example.com} and {@code ******3210} let the
 * seller match a row to a person they are already talking to, while a leaked file does not become a
 * mailing list.
 */
public final class Masking {

    private Masking() {
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return "*".repeat(local.length()) + domain;
        }
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1) + domain;
    }

    public static String mobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return "";
        }
        String trimmed = mobile.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }
        int visible = 4;
        int masked = trimmed.length() - visible;
        return "*".repeat(masked) + trimmed.substring(masked);
    }
}
