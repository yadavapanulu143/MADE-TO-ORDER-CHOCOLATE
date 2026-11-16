package com.choco.gateway.ratelimit;

import com.choco.core.port.RateLimiter;
import com.choco.gateway.config.GatewayProperties;
import com.choco.web.client.ClientIpResolver;
import com.choco.web.filter.CorrelationIdFilter;
import com.choco.web.json.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coarse per-address throttle at the edge.
 *
 * <p>Its job is to shed a flood before it reaches a service, not to enforce the business limits. The
 * identity service still applies its own, finer limits (per identifier as well as per address, with
 * separate counters for login, OTP and reset); this filter is the cheaper first line that stops an
 * obviously abusive client from consuming a service's threads at all. Keeping both is intentional:
 * the edge limit is coarse and cheap, the service limit is precise and authoritative.
 *
 * <p>The counter is shared through Redis, so the limit is the same across every gateway replica. An
 * in-process counter would multiply the allowance by the number of replicas — with three gateways,
 * "60 auth requests per minute" becomes 180.
 *
 * <p>Authentication paths get the tighter allowance. That is where a credential-stuffing or OTP-guessing
 * run pays off, and a real client touches those routes only a handful of times per session.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EdgeRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EdgeRateLimitFilter.class);

    /** Paths throttled at the lower auth allowance. */
    private static final String[] AUTH_PREFIXES = {
            "/api/v1/auth/", "/api/v1/payments/", "/api/v1/webhooks/"
    };

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public EdgeRateLimitFilter(RateLimiter rateLimiter,
                               ClientIpResolver clientIpResolver,
                               GatewayProperties properties,
                               ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        GatewayProperties.RateLimit config = properties.getRateLimit();
        if (!config.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        boolean authPath = isAuthPath(path);
        int limit = authPath ? config.getAuthRequestsPerWindow() : config.getRequestsPerWindow();

        String address = clientIpResolver.resolve(request);
        String key = "gw:rl:" + (authPath ? "auth:" : "api:") + address;

        RateLimiter.Decision decision = rateLimiter.tryConsume(key, limit, config.getWindow().toSeconds());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        log.info("Edge rate limit hit for {} on {} {} [correlationId={}]",
                address, request.getMethod(), path, correlationId(request));
        writeRateLimited(response, decision, request);
    }

    private boolean isAuthPath(String path) {
        for (String prefix : AUTH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeRateLimited(HttpServletResponse response, RateLimiter.Decision decision,
                                  HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setHeader("X-RateLimit-Remaining", "0");

        ApiError error = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "RATE_LIMITED",
                "Too many requests. Please slow down and retry later.",
                request.getRequestURI(),
                correlationId(request),
                null);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
