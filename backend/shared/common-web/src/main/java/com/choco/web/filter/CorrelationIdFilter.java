package com.choco.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id, echoes it back and puts it in the logging context.
 *
 * <p>An inbound id is accepted only if it looks like a bounded, safe token: reflecting arbitrary
 * client input into a response header and every log line would be a header-injection and
 * log-forging vector.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_LENGTH = 64;
    private static final String SAFE_PATTERN = "[A-Za-z0-9._-]{1," + MAX_LENGTH + "}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = sanitise(request.getHeader(HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String sanitise(String candidate) {
        if (candidate == null || candidate.isBlank() || !candidate.matches(SAFE_PATTERN)) {
            return null;
        }
        return candidate;
    }
}
