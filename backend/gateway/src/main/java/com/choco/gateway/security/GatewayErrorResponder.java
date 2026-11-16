package com.choco.gateway.security;

import com.choco.web.json.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders authentication and authorization failures as the same RFC 9457 problem detail every other
 * error in the platform uses.
 *
 * <p>Without this, Spring Security answers a rejected request with an empty body (and, for a bearer
 * scheme, a {@code WWW-Authenticate} header). A client would then face two error shapes — a structured
 * body from a service and a blank 401 from the edge — and have to special-case the gateway. One shape
 * everywhere keeps the frontend's error handling to a single code path.
 *
 * <p>The message is deliberately generic. Telling a caller whether a token was missing, malformed,
 * expired or valid-but-unauthorized helps an attacker tune a forgery attempt and helps a legitimate
 * user not at all; the correlation id is what support needs to investigate.
 */
@Component
public class GatewayErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication is required to access this resource.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You are not allowed to access this resource.");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                       String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Object correlationId = request.getAttribute("correlationId");
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), code, detail,
                request.getRequestURI(),
                correlationId == null ? null : correlationId.toString(),
                null);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
