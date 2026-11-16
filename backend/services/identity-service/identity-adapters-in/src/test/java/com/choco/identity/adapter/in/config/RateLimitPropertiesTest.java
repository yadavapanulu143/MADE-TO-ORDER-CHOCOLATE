package com.choco.identity.adapter.in.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.identity.application.service.AbuseGuard;
import org.junit.jupiter.api.Test;

/**
 * Guards against the configuration drifting from the policy defaults.
 *
 * <p>This is a regression test with a specific history. The limits were once declared twice — once in
 * {@link AbuseGuard.Policy#defaults()} and once as a literal in a {@code @Value} default — and the two
 * were allowed to disagree. The values that took effect were the duplicates, so the login limit fired
 * below the account-lockout threshold and every locked-out customer received a generic rate-limit
 * error instead of the specific one. Nothing failed; the behaviour was simply wrong.
 *
 * <p>The test asserts the two agree, which fails the moment anyone edits one and not the other.
 */
class RateLimitPropertiesTest {

    @Test
    void bound_defaults_match_the_policy_defaults() {
        AbuseGuard.Policy expected = AbuseGuard.Policy.defaults();

        assertThat(new RateLimitProperties().toPolicy())
                .as("the bound configuration must not restate the limits with different values")
                .isEqualTo(expected);
    }
}
