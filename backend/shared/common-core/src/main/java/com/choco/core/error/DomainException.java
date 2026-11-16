package com.choco.core.error;

import java.util.Objects;

/**
 * Base for every expected, business-level failure. Unexpected failures are not represented here:
 * they bubble up as runtime exceptions and become {@code INTERNAL_ERROR} without leaking detail.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
