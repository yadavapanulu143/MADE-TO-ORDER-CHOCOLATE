package com.choco.core.port;

import java.util.List;

/**
 * Outbound port for password strength rules.
 *
 * <p>Returns the violations rather than throwing, so a caller can decide how to present them. The
 * rules themselves (minimum length, blocklist) are configuration, not business rules, which is why
 * they live behind a port.
 */
public interface PasswordPolicy {

    /** @return every rule the password fails; empty when it is acceptable. */
    List<String> violations(String rawPassword);
}
