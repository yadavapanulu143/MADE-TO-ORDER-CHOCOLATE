package com.choco.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the gateway's configuration binding and its defaults.
 *
 * <p>Defaults matter here: a missing public-path entry locks users out of sign-in, and a missing
 * service URI would silently drop a route. The tests pin the behaviour that a blank or null input
 * degrades to an empty collection rather than a null that later throws a
 * {@code NullPointerException} deep inside the filter chain.
 */
class GatewayPropertiesTest {

    @Nested
    @DisplayName("service lookup")
    class ServiceLookup {

        @Test
        void resolvesConfiguredService() {
            GatewayProperties properties = new GatewayProperties();
            properties.setServices(Map.of("identity", URI.create("http://identity:8081")));

            assertThat(properties.service("identity")).isEqualTo(URI.create("http://identity:8081"));
        }

        @Test
        void returnsNullForUnknownService() {
            GatewayProperties properties = new GatewayProperties();
            properties.setServices(Map.of("identity", URI.create("http://identity:8081")));

            assertThat(properties.service("catalog")).isNull();
        }

        @Test
        void shipsWithEveryPlannedServiceByDefault() {
            GatewayProperties properties = new GatewayProperties();

            assertThat(properties.getServices())
                    .containsKeys("identity", "catalog", "order", "payment",
                            "fulfilment", "notification", "reporting");
        }

        @Test
        void nullServicesDegradesToEmptyMap() {
            GatewayProperties properties = new GatewayProperties();
            properties.setServices(null);

            assertThat(properties.getServices()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("public paths")
    class PublicPaths {

        @Test
        void opensRegistrationAndSignInByDefault() {
            GatewayProperties properties = new GatewayProperties();

            assertThat(properties.getPublicPaths())
                    .contains("/api/v1/auth/register", "/api/v1/auth/login",
                            "/api/v1/auth/admin/login", "/.well-known/jwks.json");
        }

        @Test
        void opensReadOnlyCatalogueAndServiceability() {
            GatewayProperties properties = new GatewayProperties();

            assertThat(properties.getPublicPaths())
                    .contains("/api/v1/products/**", "/api/v1/serviceability/**");
        }

        @Test
        void doesNotOpenCustomerProfileOrOrders() {
            GatewayProperties properties = new GatewayProperties();

            // The customer profile and order endpoints must never be public: they expose one
            // customer's data and must require a token so ownership can be checked downstream.
            assertThat(properties.getPublicPaths())
                    .noneMatch(path -> path.equals("/api/v1/customers/**")
                            || path.startsWith("/api/v1/orders"));
        }

        @Test
        void nullPublicPathsDegradesToEmptyList() {
            GatewayProperties properties = new GatewayProperties();
            properties.setPublicPaths(null);

            assertThat(properties.getPublicPaths()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("rate limit defaults")
    class RateLimitDefaults {

        @Test
        void isEnabledWithAOneMinuteWindowByDefault() {
            GatewayProperties properties = new GatewayProperties();

            assertThat(properties.getRateLimit().isEnabled()).isTrue();
            assertThat(properties.getRateLimit().getWindow()).isEqualTo(Duration.ofMinutes(1));
        }

        @Test
        void authAllowanceIsTighterThanTheGeneralAllowance() {
            GatewayProperties properties = new GatewayProperties();
            GatewayProperties.RateLimit limit = properties.getRateLimit();

            // Authentication is where a brute-force attempt pays off, so its allowance must be the
            // smaller of the two; an inversion here would weaken the edge throttle.
            assertThat(limit.getAuthRequestsPerWindow()).isLessThan(limit.getRequestsPerWindow());
        }

        @Test
        void exposesConfigurableValues() {
            GatewayProperties properties = new GatewayProperties();
            properties.getRateLimit().setRequestsPerWindow(1000);
            properties.getRateLimit().setAuthRequestsPerWindow(25);
            properties.getRateLimit().setWindow(Duration.ofSeconds(30));
            properties.getRateLimit().setEnabled(false);

            assertThat(properties.getRateLimit().getRequestsPerWindow()).isEqualTo(1000);
            assertThat(properties.getRateLimit().getAuthRequestsPerWindow()).isEqualTo(25);
            assertThat(properties.getRateLimit().getWindow()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getRateLimit().isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("http client defaults")
    class HttpClientDefaults {

        @Test
        void appliesFiniteTimeoutsByDefault() {
            GatewayProperties properties = new GatewayProperties();

            // A missing read timeout is the classic cause of a gateway whose threads are all parked
            // on one slow downstream service.
            assertThat(properties.getHttpClient().getConnectTimeout()).isNotNull().isPositive();
            assertThat(properties.getHttpClient().getReadTimeout()).isNotNull().isPositive();
        }

        @Test
        void exposesConfigurableTimeouts() {
            GatewayProperties properties = new GatewayProperties();
            properties.getHttpClient().setConnectTimeout(Duration.ofSeconds(1));
            properties.getHttpClient().setReadTimeout(Duration.ofSeconds(3));

            assertThat(properties.getHttpClient().getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getHttpClient().getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
        }
    }
}
