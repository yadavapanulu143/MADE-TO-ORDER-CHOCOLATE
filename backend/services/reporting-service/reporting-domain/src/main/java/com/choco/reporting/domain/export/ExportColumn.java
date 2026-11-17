package com.choco.reporting.domain.export;

import java.util.Locale;
import java.util.Set;

/**
 * A column of an export: the key the value is read under and the header printed on the document.
 *
 * <p>Declaring both together is what keeps an export honest. A column exists only because it is named
 * here, so a value can never leak into a spreadsheet merely because a query happened to select it —
 * the mapping is explicit and reviewable.
 *
 * <p>A column whose key looks like an authentication artefact (a password, a hash, an OTP, a token, a
 * secret) is refused at construction. That is a last-line guard: the real protection is that the
 * datasets never select those fields, but a future edit that tried to add one fails loudly here and
 * in the accompanying test rather than quietly shipping a credential into a download.
 */
public record ExportColumn(String key, String header) {

    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
            "password", "passwd", "hash", "otp", "token", "secret", "cvv", "pin");

    public ExportColumn {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("An export column needs a key");
        }
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("An export column needs a header");
        }
        String normalised = key.toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_FRAGMENTS) {
            if (normalised.contains(forbidden)) {
                throw new IllegalArgumentException(
                        "Export column '" + key + "' names an authentication artefact and must never "
                                + "be exported");
            }
        }
        key = key.trim();
        header = header.trim();
    }
}
