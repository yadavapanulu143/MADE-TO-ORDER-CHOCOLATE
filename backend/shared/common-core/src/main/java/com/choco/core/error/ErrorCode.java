package com.choco.core.error;

/**
 * Stable, machine-readable error identifiers. Clients switch on these values, never on the
 * human-readable message, so wording can change without breaking a caller.
 */
public enum ErrorCode {

    VALIDATION_FAILED(400),
    MALFORMED_REQUEST(400),
    UNAUTHENTICATED(401),
    INVALID_CREDENTIALS(401),
    TOKEN_EXPIRED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    CONFLICT(409),
    IDEMPOTENCY_KEY_REUSED(409),
    RATE_LIMITED(429),
    OTP_EXPIRED(410),
    OTP_INVALID(400),
    OTP_ATTEMPTS_EXCEEDED(429),
    EMAIL_NOT_VERIFIED(403),
    MOBILE_NOT_VERIFIED(403),
    ACCOUNT_LOCKED(423),
    EMAIL_ALREADY_REGISTERED(409),
    MOBILE_ALREADY_REGISTERED(409),
    WEAK_PASSWORD(400),
    CUSTOMER_NOT_FOUND(404),
    ADMIN_NOT_FOUND(404),
    ADDRESS_NOT_FOUND(404),
    ADDRESS_LIMIT_REACHED(409),
    RESET_TOKEN_INVALID(400),
    RESET_TOKEN_EXPIRED(410),
    PRODUCT_UNAVAILABLE(409),
    QUANTITY_LIMIT_EXCEEDED(409),
    PINCODE_NOT_SERVICEABLE(422),
    INVALID_STATUS_TRANSITION(409),
    PAYMENT_SIGNATURE_INVALID(400),
    PAYMENT_ALREADY_CAPTURED(409),
    PAYMENT_VERIFICATION_FAILED(402),
    PAYMENT_AMOUNT_MISMATCH(409),
    DEPENDENCY_UNAVAILABLE(503),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
