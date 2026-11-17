package com.choco.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Edge configuration: where each downstream service lives, which paths are public, and the coarse
 * per-IP throttle applied before a request is forwarded.
 *
 * <p>Bound as a typed object rather than read with {@code @Value} because several settings are
 * collections. {@code @Value} cannot bind a YAML sequence — it resolves a single scalar and silently
 * leaves a list empty, which for a public-path list would mean either locking out sign-in or, if the
 * list were inverted, exposing protected routes. A typed binding fails loudly on a malformed value.
 */
@ConfigurationProperties(prefix = "choco.gateway")
public class GatewayProperties {

    /** Base URI of each downstream service, keyed by service name. */
    private Map<String, URI> services = defaultServices();

    /**
     * Paths reachable without a token.
     *
     * <p>Declared explicitly rather than derived from a wildcard, so widening public access is a
     * visible edit in configuration. The list mirrors the identity service's own public paths plus
     * the read-only catalogue and the serviceability probe the storefront needs before sign-in.
     */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/admin/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/complete",
            "/api/v1/auth/customers/**",
            "/.well-known/jwks.json",
            "/api/v1/products",
            "/api/v1/products/**",
            "/api/v1/catalog/images/**",
            "/api/v1/settings",
            "/api/v1/serviceability/**"));

    private final RateLimit rateLimit = new RateLimit();
    private final HttpClient httpClient = new HttpClient();

    private static Map<String, URI> defaultServices() {
        Map<String, URI> services = new LinkedHashMap<>();
        services.put("identity", URI.create("http://localhost:8081"));
        services.put("catalog", URI.create("http://localhost:8082"));
        services.put("order", URI.create("http://localhost:8083"));
        services.put("payment", URI.create("http://localhost:8084"));
        services.put("fulfilment", URI.create("http://localhost:8085"));
        services.put("notification", URI.create("http://localhost:8086"));
        services.put("reporting", URI.create("http://localhost:8087"));
        return services;
    }

    /** Coarse edge throttle, applied per client address before routing. */
    public static class RateLimit {

        private boolean enabled = true;

        /** Requests allowed per window from one address, across all routes. */
        private int requestsPerWindow = 300;

        /**
         * Tighter allowance for authentication routes, where an attacker's payoff is highest and a
         * legitimate client makes only a handful of calls. The service re-applies its own, finer
         * limits; this only keeps a flood from reaching it.
         */
        private int authRequestsPerWindow = 60;

        private Duration window = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerWindow() {
            return requestsPerWindow;
        }

        public void setRequestsPerWindow(int requestsPerWindow) {
            this.requestsPerWindow = requestsPerWindow;
        }

        public int getAuthRequestsPerWindow() {
            return authRequestsPerWindow;
        }

        public void setAuthRequestsPerWindow(int authRequestsPerWindow) {
            this.authRequestsPerWindow = authRequestsPerWindow;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    /** Timeouts for the proxy's outbound calls, so a stalled service cannot pin a gateway thread. */
    public static class HttpClient {

        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(10);

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public Map<String, URI> getServices() {
        return services;
    }

    public void setServices(Map<String, URI> services) {
        this.services = services == null ? new LinkedHashMap<>() : services;
    }

    /** Base URI of a named service, or {@code null} when it is not configured. */
    public URI service(String name) {
        return services.get(name);
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths == null ? new ArrayList<>() : publicPaths;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }
}
