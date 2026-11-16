package com.choco.web.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * RFC 9457 problem detail extended with a stable {@code code} and a per-field breakdown.
 *
 * <p>Clients are told what went wrong in a form they can branch on, while internal exception
 * messages and stack traces never reach the response body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String code,
        String instance,
        String correlationId,
        Instant timestamp,
        Map<String, String> fieldErrors) {

    public static final String DEFAULT_TYPE = "about:blank";

    public static ApiError of(int status, String title, String code, String detail, String instance,
                              String correlationId, Map<String, String> fieldErrors) {
        return new ApiError(DEFAULT_TYPE, title, status, detail, code, instance, correlationId,
                Instant.now(), fieldErrors);
    }
}
