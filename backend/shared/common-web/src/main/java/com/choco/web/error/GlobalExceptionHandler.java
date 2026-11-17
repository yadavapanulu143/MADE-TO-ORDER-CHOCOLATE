package com.choco.web.error;

import com.choco.core.error.DomainException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.RateLimitedException;
import com.choco.core.error.ValidationException;
import com.choco.web.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single place that turns exceptions into RFC 9457 responses.
 *
 * <p>Unexpected exceptions are logged with the correlation id and returned as a generic 500: the
 * message is deliberately not echoed back, because exception text can leak schema, SQL or internal
 * hostnames to an attacker.
 *
 * <p>Registered at the lowest precedence because it is the fallback. Its {@code Exception} handler
 * matches everything, and a {@code @ControllerAdvice} is consulted in order, first match wins — so a
 * higher-precedence advice with a more specific handler (the security module's 403 for a denied
 * {@code @PreAuthorize}, for example) must be reached first. At the highest precedence this advice
 * would swallow those cases and report a permission refusal as a server error.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<com.choco.web.json.ApiError> handleDomain(
            DomainException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        Map<String, String> fieldErrors = ex instanceof ValidationException validation
                ? validation.fieldErrors()
                : null;

        log.debug("Business error {} on {} {}: {}", code, request.getMethod(), request.getRequestURI(),
                ex.getMessage());

        return build(code, ex.getMessage(), fieldErrors, request);
    }

    /**
     * Answers a rate-limited caller with {@code Retry-After}.
     *
     * <p>The header is the difference between a client that backs off and one that retries
     * immediately in a loop. {@code Retry-After} is the standard signal and is understood by browsers
     * and HTTP clients without any custom handling.
     */
    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<com.choco.web.json.ApiError> handleRateLimited(
            RateLimitedException ex, HttpServletRequest request) {
        log.info("Rate limit hit on {} {}: {} [correlationId={}]",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), correlationId(request));

        ResponseEntity<com.choco.web.json.ApiError> response =
                build(ex.errorCode(), ex.getMessage(), null, request);
        return ResponseEntity.status(response.getStatusCode())
                .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<com.choco.web.json.ApiError> handleBeanValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage());
        }
        return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", fieldErrors, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<com.choco.web.json.ApiError> handleMalformed(Exception ex, HttpServletRequest request) {
        log.debug("Malformed request on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.MALFORMED_REQUEST, "Malformed or unreadable request", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<com.choco.web.json.ApiError> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, "HTTP method not supported for this resource", null, request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<com.choco.web.json.ApiError> handleNotFound(
            Exception ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, "Resource not found", null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<com.choco.web.json.ApiError> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        // Domain value objects throw this for bad input; it is safe to surface the reason.
        return build(ErrorCode.VALIDATION_FAILED, ex.getMessage(), null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<com.choco.web.json.ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {} [correlationId={}]",
                request.getMethod(), request.getRequestURI(), correlationId(request), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please retry later.", null, request);
    }

    private ResponseEntity<com.choco.web.json.ApiError> build(ErrorCode code, String detail,
                                                              Map<String, String> fieldErrors,
                                                              HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(code.httpStatus());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        com.choco.web.json.ApiError body = com.choco.web.json.ApiError.of(
                resolved.value(),
                resolved.getReasonPhrase(),
                code.name(),
                detail,
                request.getRequestURI(),
                correlationId(request),
                fieldErrors);
        return ResponseEntity.status(resolved).body(body);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
