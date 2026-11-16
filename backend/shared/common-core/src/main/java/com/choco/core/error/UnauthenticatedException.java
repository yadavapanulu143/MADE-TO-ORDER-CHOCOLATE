package com.choco.core.error;

/**
 * The caller is not authenticated, or the presented credentials are not valid.
 *
 * <p>Distinct from {@link ForbiddenException}: here the identity itself could not be established,
 * so the response must never reveal whether the account exists.
 */
public class UnauthenticatedException extends DomainException {

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }

    public UnauthenticatedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
