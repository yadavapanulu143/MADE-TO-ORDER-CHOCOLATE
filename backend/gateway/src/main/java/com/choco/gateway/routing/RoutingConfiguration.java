package com.choco.gateway.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import com.choco.gateway.config.GatewayProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Declares one route per downstream service.
 *
 * <p>Routes are defined in Java rather than in YAML so a reviewer can see, in one place, which path
 * prefix belongs to which service and which resilience policy wraps it. The upstream addresses are
 * still externalised through {@link GatewayProperties}, so a deployment points at real hosts without
 * touching this file.
 *
 * <p>Each route that crosses the network is wrapped in a retry and a circuit breaker:
 * <ul>
 *   <li><b>Retry</b> only for {@code GET} and only on a server error. Retrying a {@code POST} at the
 *       gateway is unsafe: the first attempt may have been applied even though the response was lost,
 *       and a second attempt would double-charge or double-create. Idempotency for writes is the
 *       owning service's job, using an idempotency key, not a blind transport retry.</li>
 *   <li><b>Circuit breaker</b> so that when a service is down the gateway fails fast with a clear
 *       503 instead of queueing connections until its own threads are exhausted, which would take
 *       the whole platform down with one sick service.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class RoutingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RoutingConfiguration.class);

    /** Path prefix of a circuit-breaker fallback route, served by the gateway itself. */
    public static final String FALLBACK_PREFIX = "/fallback/";

    @Bean
    RouterFunction<ServerResponse> identityRoutes(GatewayProperties properties) {
        return serviceRoute(properties, "identity",
                "/api/v1/auth/**", "/api/v1/customers/**", "/.well-known/jwks.json");
    }

    @Bean
    RouterFunction<ServerResponse> catalogRoutes(GatewayProperties properties) {
        return serviceRoute(properties, "catalog",
                "/api/v1/products/**", "/api/v1/catalog/**", "/api/v1/settings/**",
                "/api/v1/settings", "/api/v1/admin/catalog/**");
    }

    @Bean
    RouterFunction<ServerResponse> orderRoutes(GatewayProperties properties) {
        return serviceRoute(properties, "order",
                "/api/v1/carts/**", "/api/v1/orders/**", "/api/v1/invoices/**",
                "/api/v1/cancellations/**", "/api/v1/refunds/**");
    }

    @Bean
    RouterFunction<ServerResponse> paymentRoutes(GatewayProperties properties) {
        return serviceRoute(properties, "payment",
                "/api/v1/payments/**", "/api/v1/webhooks/**");
    }

    @Bean
    RouterFunction<ServerResponse> fulfilmentRoutes(GatewayProperties properties) {
        return serviceRoute(properties, "fulfilment",
                "/api/v1/production/**", "/api/v1/deliveries/**", "/api/v1/serviceability/**",
                "/api/v1/delivery-charges/**", "/api/v1/ingredients/**");
    }

    @Bean
    RouterFunction<ServerResponse> notificationRoutes(GatewayProperties properties) {
        return serviceRoute(properties, "notification", "/api/v1/notifications/**");
    }

    @Bean
    RouterFunction<ServerResponse> reportingRoutes(GatewayProperties properties) {
        return serviceRoute(properties, "reporting",
                "/api/v1/reports/**", "/api/v1/exports/**", "/api/v1/dashboard/**");
    }

    /**
     * Builds a route that forwards the given path patterns to a named service.
     *
     * <p>When the service is not configured the route is not registered, and a request to it falls
     * through to a 404. That is deliberate: a service that has not been deployed yet should look
     * absent, not forward to a guessed default host.
     */
    private RouterFunction<ServerResponse> serviceRoute(GatewayProperties properties, String serviceName,
                                                        String... pathPatterns) {
        URI target = properties.service(serviceName);
        if (target == null) {
            log.warn("No upstream configured for service '{}'; its routes are not registered", serviceName);
            return null;
        }

        var predicate = path(pathPatterns[0]);
        for (int i = 1; i < pathPatterns.length; i++) {
            predicate = predicate.or(path(pathPatterns[i]));
        }

        return GatewayRouterFunctions.route(serviceName)
                .route(predicate, HandlerFunctions.http())
                .filter(uri(target.toString()))
                .filter(RetryFilterFunctions.retry(retry -> retry
                        .setRetries(2)
                        .setSeries(java.util.Set.of(HttpStatus.Series.SERVER_ERROR))
                        .setMethods(java.util.Set.of(HttpMethod.GET))
                        .setCacheBody(false)))
                .filter(CircuitBreakerFilterFunctions.circuitBreaker(breaker -> breaker
                        .setId(serviceName)
                        // A 5xx response is the service telling us it failed, but it is still a
                        // response, not an exception, so the breaker does not count it unless it is
                        // named here. Without this list the breaker would only trip on a connection
                        // failure and would happily pass a steady stream of 500s straight through.
                        .setStatusCodes("500", "502", "503", "504")
                        .setFallbackUri("forward:" + FALLBACK_PREFIX + serviceName)))
                .build();
    }

    /**
     * The target the circuit breaker redirects to when a service is unavailable.
     *
     * <p>Defined as a plain route so it is always reachable, including while a breaker is open. The
     * body is an RFC 9457 problem detail matching every other error the platform returns, so a client
     * parses one error shape whether the failure happened in the gateway or in a service.
     */
    @Bean
    RouterFunction<ServerResponse> fallbackRoutes() {
        return route("fallback")
                .route(path(FALLBACK_PREFIX + "**"), request -> {
                    String service = request.uri().getPath().substring(FALLBACK_PREFIX.length());
                    log.warn("Circuit open or upstream failure for service '{}' [correlationId={}]",
                            service, correlationId(request));
                    return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Retry-After", "5")
                            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            .body(com.choco.web.json.ApiError.of(
                                    503,
                                    "Service Unavailable",
                                    "DEPENDENCY_UNAVAILABLE",
                                    "This service is temporarily unavailable. Please retry shortly.",
                                    request.uri().getPath(),
                                    correlationId(request),
                                    null));
                })
                .build();
    }

    private static String correlationId(org.springframework.web.servlet.function.ServerRequest request) {
        Object value = request.servletRequest()
                .getAttribute(com.choco.web.filter.CorrelationIdFilter.ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
