package com.choco.core.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Invalid input. Field errors are kept separately so the API can return a per-field breakdown
 * instead of one opaque message.
 */
public class ValidationException extends DomainException {

    private final transient Map<String, String> fieldErrors;

    public ValidationException(String message) {
        this(message, Map.of());
    }

    public ValidationException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        this(ErrorCode.VALIDATION_FAILED, message, fieldErrors);
    }

    public ValidationException(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        super(errorCode, message);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
