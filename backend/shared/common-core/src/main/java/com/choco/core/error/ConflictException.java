package com.choco.core.error;

public class ConflictException extends DomainException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
