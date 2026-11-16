package com.choco.core.error;

/**
 * A domain invariant was violated by a well-formed request, e.g. an illegal order-status
 * transition. Distinct from {@link ValidationException}, which is about malformed input.
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
