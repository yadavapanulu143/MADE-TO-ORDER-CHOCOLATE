package com.choco.identity.domain.otp;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A one-time password challenge for one verification channel.
 *
 * <p>Only the hash of the code is ever stored, so a database leak does not hand an attacker a valid
 * OTP. The record also carries the three limits that make brute force impractical: a short expiry, a
 * hard cap on verification attempts, and a minimum interval between resends.
 *
 * <p>Deliberately a plain object, not an aggregate: a challenge has no invariants spanning other
 * entities, and its lifecycle is short.
 */
public class OtpChallenge {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration MIN_RESEND_INTERVAL = Duration.ofSeconds(60);
    public static final int MAX_RESENDS = 5;
    private static final int CODE_LENGTH = 6;

    private final UUID id;
    private final UUID customerId;
    private final String channel;
    private final String destination;
    private final String codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int attempts;
    private int resendCount;
    private Instant consumedAt;

    private OtpChallenge(UUID id, UUID customerId, String channel, String destination, String codeHash,
                         Instant createdAt, Instant expiresAt, int attempts, int resendCount, Instant consumedAt) {
        this.id = id;
        this.customerId = customerId;
        this.channel = channel;
        this.destination = destination;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attempts = attempts;
        this.resendCount = resendCount;
        this.consumedAt = consumedAt;
    }

    public static OtpChallenge issue(UUID id, UUID customerId, String channel, String destination,
                                     String codeHash, Instant now) {
        return new OtpChallenge(id, customerId, channel, destination, codeHash, now,
                now.plus(DEFAULT_TTL), 0, 0, null);
    }

    public static OtpChallenge rehydrate(UUID id, UUID customerId, String channel, String destination,
                                         String codeHash, Instant createdAt, Instant expiresAt, int attempts,
                                         int resendCount, Instant consumedAt) {
        return new OtpChallenge(id, customerId, channel, destination, codeHash, createdAt, expiresAt,
                attempts, resendCount, consumedAt);
    }

    /** Replaces the code after a resend, keeping the challenge identity and the resend counter. */
    public OtpChallenge withNewCode(String newCodeHash, Instant now) {
        assertResendAllowed(now);
        return new OtpChallenge(id, customerId, channel, destination, newCodeHash, now,
                now.plus(DEFAULT_TTL), 0, resendCount + 1, null);
    }

    public void assertResendAllowed(Instant now) {
        if (isConsumed()) {
            throw new BusinessRuleException(ErrorCode.OTP_INVALID,
                    "This verification has already been completed");
        }
        if (resendCount >= MAX_RESENDS) {
            throw new BusinessRuleException(ErrorCode.RATE_LIMITED,
                    "Too many verification codes requested. Please contact support.");
        }
        if (now.isBefore(createdAt.plus(MIN_RESEND_INTERVAL))) {
            long waitSeconds = MIN_RESEND_INTERVAL.minus(Duration.between(createdAt, now)).toSeconds() + 1;
            throw new BusinessRuleException(ErrorCode.RATE_LIMITED,
                    "Please wait " + waitSeconds + " seconds before requesting another code");
        }
    }

    /**
     * Records a verification attempt.
     *
     * @param matched whether the submitted code matched the stored hash
     * @throws BusinessRuleException when the challenge is expired, already used, or out of attempts
     */
    public void attempt(boolean matched, Instant now) {
        if (isConsumed()) {
            throw new BusinessRuleException(ErrorCode.OTP_INVALID,
                    "This verification has already been completed");
        }
        if (isExpired(now)) {
            throw new BusinessRuleException(ErrorCode.OTP_EXPIRED,
                    "The verification code has expired. Please request a new one.");
        }
        if (attempts >= MAX_ATTEMPTS) {
            throw new BusinessRuleException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    "Too many incorrect attempts. Please request a new code.");
        }

        attempts++;
        if (matched) {
            consumedAt = now;
        } else if (attempts >= MAX_ATTEMPTS) {
            throw new BusinessRuleException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    "Too many incorrect attempts. Please request a new code.");
        } else {
            throw new BusinessRuleException(ErrorCode.OTP_INVALID, "The verification code is incorrect");
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isUsable(Instant now) {
        return !isConsumed() && !isExpired(now) && attempts < MAX_ATTEMPTS;
    }

    public int attemptsRemaining() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public String channel() {
        return channel;
    }

    public String destination() {
        return destination;
    }

    public String codeHash() {
        return codeHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public int attempts() {
        return attempts;
    }

    public int resendCount() {
        return resendCount;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public static int codeLength() {
        return CODE_LENGTH;
    }
}
