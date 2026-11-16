package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.RateLimitedException;
import com.choco.identity.application.fake.TestAbuseGuard;
import com.choco.test.Fakes;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the authentication rate limits.
 *
 * <p>These tests matter more than most, because a rate limiter that silently does nothing looks
 * exactly like one that works. Each test drives the guard past a limit and asserts the refusal, so a
 * regression that disables the check fails here rather than in production.
 */
class AbuseGuardTest {

    private static final String IDENTIFIER = "asha@example.com";
    private static final String ADMIN_IDENTIFIER = "owner@choco.example";
    private static final String IP = "203.0.113.7";

    private Fakes.InMemoryRateLimiter limiter;
    private AbuseGuard guard;

    @BeforeEach
    void setUp() {
        limiter = new Fakes.InMemoryRateLimiter();
        guard = TestAbuseGuard.create(limiter);
    }

    @Test
    void repeated_login_attempts_for_one_account_are_refused() {
        int limit = AbuseGuard.Policy.defaults().loginAttemptsPerIdentifier();

        for (int i = 0; i < limit; i++) {
            int attempt = i;
            assertThatCode(() -> guard.checkLogin(IDENTIFIER, IP))
                    .as("attempt %d of %d must be allowed", attempt + 1, limit)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> guard.checkLogin(IDENTIFIER, IP))
                .as("the attempt after the limit must be refused")
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void login_is_also_limited_per_source_address() {
        int limit = AbuseGuard.Policy.defaults().loginAttemptsPerIp();

        // A different account each time, which is the credential-stuffing shape: the per-account
        // limit alone would never fire.
        for (int i = 0; i < limit; i++) {
            guard.checkLogin("customer" + i + "@example.com", IP);
        }

        assertThatThrownBy(() -> guard.checkLogin("another@example.com", IP))
                .as("spraying many accounts from one address must be stopped")
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void a_successful_login_clears_the_account_counter_but_not_the_address_counter() {
        guard.checkLogin(IDENTIFIER, IP);
        guard.loginSucceeded(IDENTIFIER);

        assertThat(limiter.currentCount("rl:login:id:hash:" + IDENTIFIER))
                .as("the account counter is cleared so a mistyping customer starts clean")
                .isZero();
        assertThat(limiter.currentCount("rl:login:ip:" + IP))
                .as("the address counter must survive, or one valid password resets the network limit")
                .isEqualTo(1);
    }

    @Test
    void the_retry_delay_is_reported_so_the_client_can_back_off() {
        int limit = AbuseGuard.Policy.defaults().passwordResetsPerIdentifier();
        for (int i = 0; i < limit; i++) {
            guard.checkPasswordReset(IDENTIFIER, IP);
        }

        assertThatThrownBy(() -> guard.checkPasswordReset(IDENTIFIER, IP))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(e -> assertThat(((RateLimitedException) e).retryAfterSeconds()).isPositive());
    }

    @Test
    void password_reset_requests_are_limited_even_for_unknown_addresses() {
        int limit = AbuseGuard.Policy.defaults().passwordResetsPerIdentifier();

        // The guard never learns whether the address exists; that is the point. If it did, the 429
        // would tell an attacker which addresses are registered.
        for (int i = 0; i < limit; i++) {
            guard.checkPasswordReset("nobody@example.com", IP);
        }

        assertThatThrownBy(() -> guard.checkPasswordReset("nobody@example.com", IP))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void otp_verification_and_resend_have_separate_budgets() {
        int verificationLimit = AbuseGuard.Policy.defaults().otpVerificationsPerCustomer();
        for (int i = 0; i < verificationLimit; i++) {
            guard.checkOtpVerification("customer-1");
        }

        assertThatThrownBy(() -> guard.checkOtpVerification("customer-1"))
                .as("the verification budget is exhausted")
                .isInstanceOf(RateLimitedException.class);

        // A separate counter, so exhausting one does not block the other: a customer who mistyped
        // codes must still be able to request a fresh one.
        assertThatCode(() -> guard.checkOtpResend("customer-1"))
                .as("the resend budget is independent of the verification budget")
                .doesNotThrowAnyException();
    }

    @Test
    void limits_are_scoped_per_customer() {
        int limit = AbuseGuard.Policy.defaults().otpVerificationsPerCustomer();
        for (int i = 0; i < limit; i++) {
            guard.checkOtpVerification("customer-1");
        }

        assertThatCode(() -> guard.checkOtpVerification("customer-2"))
                .as("one customer exhausting their budget must not lock out another")
                .doesNotThrowAnyException();
    }

    @Test
    void identifiers_are_hashed_so_addresses_never_appear_in_redis_keys() {
        guard.checkLogin(IDENTIFIER, IP);

        // The account key must not contain the address in any form. Redis keys are backed up,
        // replicated and emitted into metrics, so personal data in a key leaks widely.
        assertThat(limiter.currentCount("rl:login:id:" + IDENTIFIER))
                .as("the raw address must not be the key")
                .isZero();
        assertThat(limiter.currentCount("rl:login:id:hash:" + IDENTIFIER)).isEqualTo(1);
    }

    @Test
    void a_caller_with_no_source_address_is_not_blocked() {
        // Internal callers — scheduled jobs, message consumers — have no address and must not be
        // throttled against a shared placeholder key, which would make them throttle each other.
        for (int i = 0; i < 50; i++) {
            guard.checkRegistration(null);
        }

        assertThatCode(() -> guard.checkRegistration(null)).doesNotThrowAnyException();
    }

    @Test
    void repeated_admin_login_attempts_for_one_account_are_refused() {
        int limit = AbuseGuard.Policy.defaults().loginAttemptsPerIdentifier();

        for (int i = 0; i < limit; i++) {
            assertThatCode(() -> guard.checkAdminLogin(ADMIN_IDENTIFIER, IP))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> guard.checkAdminLogin(ADMIN_IDENTIFIER, IP))
                .as("the attempt after the limit must be refused")
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void admin_and_customer_login_counters_are_independent() {
        int limit = AbuseGuard.Policy.defaults().loginAttemptsPerIdentifier();

        // Exhaust the admin budget for this address.
        for (int i = 0; i < limit; i++) {
            guard.checkAdminLogin(ADMIN_IDENTIFIER, IP);
        }
        assertThatThrownBy(() -> guard.checkAdminLogin(ADMIN_IDENTIFIER, IP))
                .isInstanceOf(RateLimitedException.class);

        // A customer signing in from the same address must be unaffected: the seller being locked
        // out of the dashboard must not lock customers out of the storefront, or vice versa.
        assertThatCode(() -> guard.checkLogin(IDENTIFIER, IP)).doesNotThrowAnyException();
    }

    @Test
    void a_successful_admin_login_clears_the_admin_account_counter_only() {
        guard.checkAdminLogin(ADMIN_IDENTIFIER, IP);
        guard.checkLogin(ADMIN_IDENTIFIER, IP);

        guard.adminLoginSucceeded(ADMIN_IDENTIFIER);

        assertThat(limiter.currentCount("rl:adminlogin:id:hash:" + ADMIN_IDENTIFIER)).isZero();
        assertThat(limiter.currentCount("rl:login:id:hash:" + ADMIN_IDENTIFIER))
                .as("clearing the admin counter must not touch the customer counter")
                .isEqualTo(1);
    }

    @Test
    void a_tighter_policy_is_honoured() {
        var strict = TestAbuseGuard.withPolicy(new AbuseGuard.Policy(
                2, 2, Duration.ofMinutes(1),
                2, Duration.ofMinutes(1),
                2, Duration.ofMinutes(1),
                2, 2, Duration.ofMinutes(1),
                2, Duration.ofMinutes(1),
                2, Duration.ofMinutes(1)));

        strict.checkLogin(IDENTIFIER, IP);
        strict.checkLogin(IDENTIFIER, IP);

        assertThatThrownBy(() -> strict.checkLogin(IDENTIFIER, IP))
                .as("an operator must be able to tighten the limits without a code change")
                .isInstanceOf(RateLimitedException.class);
    }
}
