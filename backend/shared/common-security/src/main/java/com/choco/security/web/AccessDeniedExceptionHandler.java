package com.choco.security.web;

import com.choco.core.error.ErrorCode;
import com.choco.web.filter.CorrelationIdFilter;
import com.choco.web.json.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns an authorization denial into a {@code 403} instead of a {@code 500}.
 *
 * <p>URL-level rules are enforced by the filter chain, which writes its own {@code 403}. Method-level
 * rules — every {@code @PreAuthorize} — are enforced by the method interceptor, which throws
 * {@link AccessDeniedException} up through the dispatcher. Without this handler that exception falls to
 * the generic {@code Exception} handler and is reported as an unexpected server error: the caller is
 * told the server broke when in fact their token simply lacks the permission, monitoring counts a
 * client mistake as a server fault, and a real bug becomes hard to see among the false alarms.
 *
 * <p>Registered here, in the security module, so every service that uses {@code @PreAuthorize} gets the
 * same behaviour and none of them has to remember to add it. {@code AuthorizationDeniedException}
 * extends {@link AccessDeniedException}, so one handler covers both the current and the older type.
 *
 * <p>Runs at the highest precedence, ahead of the shared {@code GlobalExceptionHandler}. Resolution is
 * ordered and first match wins, and that advice's {@code Exception} handler matches everything, so it
 * is registered at the lowest precedence instead; otherwise it would be consulted first and report the
 * denial as a server error. The ordering of the two advices is what makes this handler effective, not
 * the specificity of the exception type alone.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessDeniedExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ErrorCode.FORBIDDEN.name(),
                "You do not have permission to perform this action.",
                request.getRequestURI(),
                correlationId(request),
                null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
