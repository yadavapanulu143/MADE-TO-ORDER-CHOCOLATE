package com.choco.core.error;

/** Raised when an authenticated caller is not allowed to touch the requested resource. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
