package com.choco.core.error;

/**
 * The same idempotency key was replayed with a different request body, or the original request is
 * still in flight. Returning the first response would be wrong, so the caller is told to retry.
 */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String message) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, message);
    }
}
