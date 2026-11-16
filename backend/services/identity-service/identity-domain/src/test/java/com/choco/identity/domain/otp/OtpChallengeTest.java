package com.choco.identity.domain.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OtpChallengeTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID();

    private static OtpChallenge issued() {
        return OtpChallenge.issue(UUID.randomUUID(), CUSTOMER, "EMAIL", "a@b.com", "hash", NOW);
    }

    private static ErrorCode errorCodeOf(Throwable t) {
        return ((BusinessRuleException) t).errorCode();
    }

    @Test
    void a_fresh_challenge_is_usable() {
        OtpChallenge challenge = issued();

        assertThat(challenge.isUsable(NOW)).isTrue();
        assertThat(challenge.isExpired(NOW)).isFalse();
        assertThat(challenge.attemptsRemaining()).isEqualTo(OtpChallenge.MAX_ATTEMPTS);
        assertThat(challenge.isConsumed()).isFalse();
    }

    @Test
    void a_correct_code_consumes_the_challenge() {
        OtpChallenge challenge = issued();

        challenge.attempt(true, NOW);

        assertThat(challenge.isConsumed()).isTrue();
        assertThat(challenge.isUsable(NOW)).isFalse();
    }

    @Test
    void a_consumed_challenge_cannot_be_reused() {
        OtpChallenge challenge = issued();
        challenge.attempt(true, NOW);

        assertThatThrownBy(() -> challenge.attempt(true, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.OTP_INVALID));
    }

    @Test
    void a_wrong_code_counts_an_attempt_and_is_rejected() {
        OtpChallenge challenge = issued();

        assertThatThrownBy(() -> challenge.attempt(false, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.OTP_INVALID));

        assertThat(challenge.attempts()).isEqualTo(1);
        assertThat(challenge.attemptsRemaining()).isEqualTo(OtpChallenge.MAX_ATTEMPTS - 1);
        assertThat(challenge.isConsumed()).isFalse();
    }

    @Test
    void the_challenge_locks_after_the_maximum_number_of_wrong_attempts() {
        OtpChallenge challenge = issued();

        for (int i = 1; i < OtpChallenge.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> challenge.attempt(false, NOW))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThatThrownBy(() -> challenge.attempt(false, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.OTP_ATTEMPTS_EXCEEDED));

        assertThat(challenge.isUsable(NOW)).isFalse();
        assertThatThrownBy(() -> challenge.attempt(true, NOW))
                .as("a correct code must not be accepted once attempts are exhausted")
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.OTP_ATTEMPTS_EXCEEDED));
    }

    @Test
    void an_expired_challenge_is_rejected_even_with_the_correct_code() {
        OtpChallenge challenge = issued();
        Instant afterExpiry = NOW.plus(OtpChallenge.DEFAULT_TTL).plusSeconds(1);

        assertThat(challenge.isExpired(afterExpiry)).isTrue();
        assertThatThrownBy(() -> challenge.attempt(true, afterExpiry))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    @Test
    void expiry_boundary_is_exclusive() {
        OtpChallenge challenge = issued();
        Instant exactlyAtExpiry = NOW.plus(OtpChallenge.DEFAULT_TTL);

        assertThat(challenge.isExpired(exactlyAtExpiry)).isTrue();
    }

    @Test
    void resend_is_blocked_inside_the_minimum_interval() {
        OtpChallenge challenge = issued();
        Instant tooSoon = NOW.plusSeconds(10);

        assertThatThrownBy(() -> challenge.withNewCode("hash2", tooSoon))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void resend_is_allowed_after_the_interval_and_resets_attempts() {
        OtpChallenge challenge = issued();
        assertThatThrownBy(() -> challenge.attempt(false, NOW))
                .isInstanceOf(BusinessRuleException.class);
        Instant later = NOW.plus(OtpChallenge.MIN_RESEND_INTERVAL).plusSeconds(1);

        OtpChallenge resent = challenge.withNewCode("hash2", later);

        assertThat(resent.codeHash()).isEqualTo("hash2");
        assertThat(resent.attempts())
                .as("a new code deserves a fresh attempt budget")
                .isZero();
        assertThat(resent.resendCount()).isEqualTo(1);
        assertThat(resent.id())
                .as("the challenge identity is preserved so the caller can keep one row")
                .isEqualTo(challenge.id());
    }

    @Test
    void resend_is_capped_to_prevent_sms_abuse() {
        OtpChallenge challenge = issued();
        Instant cursor = NOW;

        for (int i = 0; i < OtpChallenge.MAX_RESENDS; i++) {
            cursor = cursor.plus(OtpChallenge.MIN_RESEND_INTERVAL).plusSeconds(1);
            challenge = challenge.withNewCode("hash" + i, cursor);
        }

        Instant finalCursor = cursor.plus(OtpChallenge.MIN_RESEND_INTERVAL).plusSeconds(1);
        OtpChallenge exhausted = challenge;
        assertThatThrownBy(() -> exhausted.withNewCode("hash-final", finalCursor))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void a_consumed_challenge_cannot_be_resent() {
        OtpChallenge challenge = issued();
        challenge.attempt(true, NOW);
        Instant later = NOW.plus(OtpChallenge.MIN_RESEND_INTERVAL).plusSeconds(1);

        assertThatThrownBy(() -> challenge.withNewCode("hash2", later))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(t -> assertThat(errorCodeOf(t)).isEqualTo(ErrorCode.OTP_INVALID));
    }

    @Test
    void rehydrate_restores_a_partially_used_challenge() {
        OtpChallenge challenge = OtpChallenge.rehydrate(UUID.randomUUID(), CUSTOMER, "MOBILE",
                "+919876543210", "hash", NOW, NOW.plusSeconds(300), 2, 1, null);

        assertThat(challenge.attempts()).isEqualTo(2);
        assertThat(challenge.resendCount()).isEqualTo(1);
        assertThat(challenge.attemptsRemaining()).isEqualTo(OtpChallenge.MAX_ATTEMPTS - 2);
    }
}
