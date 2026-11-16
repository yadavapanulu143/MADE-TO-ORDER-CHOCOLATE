package com.choco.identity.domain.port;

import java.util.List;

/**
 * Outbound port for password strength rules, so the domain can reject a weak password without
 * knowing how strength is measured.
 */
public interface PasswordPolicyPort {

    /** @return every rule the password fails; empty when it is acceptable. */
    List<String> violations(String rawPassword);
}
