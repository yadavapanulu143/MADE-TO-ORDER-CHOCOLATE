package com.choco.core.error;

/**
 * Thrown when a caller exceeds an allowed rate.
 *
 * <p>Carries the retry delay so the HTTP layer can answer with a {@code Retry-After} header. Without
 * it a client has no way to distinguish "wait two seconds" from "you are locked out for an hour", and
 * the usual result is a tight retry loop that makes the overload worse.
 */
public class RateLimitedException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
