package com.choco.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies the resilience policy around a flaky downstream service.
 *
 * <p>The service is switched between "failing" and "healthy" by a flag rather than by restarting the
 * stub, so the test can watch the breaker open under failures and then close again after the service
 * recovers. That is the behaviour an operator depends on: a service that is briefly down must not
 * stay unreachable for long after it comes back.
 */
class CircuitBreakerIT extends AbstractGatewayIntegrationTest {

    private static final AtomicBoolean FAILING = new AtomicBoolean(false);
    private static final MockWebServer CATALOG = new MockWebServer();

    static {
        CATALOG.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (FAILING.get()) {
                    return new MockResponse().setResponseCode(500).setBody("{\"boom\":true}");
                }
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"ok\":true}");
            }
        });
        try {
            CATALOG.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("choco.gateway.services.catalog", () -> CATALOG.url("/").toString());
        // Keep the rate limit out of the way so this test observes only the breaker.
        registry.add("choco.gateway.rate-limit.enabled", () -> "false");
    }

    @AfterAll
    static void stop() throws Exception {
        FAILING.set(false);
        CATALOG.shutdown();
    }

    @Test
    @DisplayName("when a service fails repeatedly the gateway answers 503 instead of propagating the error")
    void openBreakerReturnsServiceUnavailable() {
        FAILING.set(true);
        HttpResponse<String> last = null;
        try {
            // Enough calls to exceed the minimum sample and the failure threshold. The route is a
            // GET, so the retry filter also applies; the breaker still counts the final outcomes.
            for (int i = 0; i < 12; i++) {
                last = get("/api/v1/products/", null);
            }
        } finally {
            FAILING.set(false);
        }

        assertThat(last).isNotNull();
        assertThat(last.statusCode()).isEqualTo(503);
        assertThat(json(last).get("code").asText()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(last.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    @DisplayName("the failure body is the same problem detail shape as every other error")
    void failureBodyIsProblemDetail() {
        FAILING.set(true);
        HttpResponse<String> last = null;
        try {
            for (int i = 0; i < 12; i++) {
                last = get("/api/v1/products/", null);
            }
        } finally {
            FAILING.set(false);
        }

        assertThat(last).isNotNull();
        assertThat(last.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
        assertThat(json(last).has("code")).isTrue();
        assertThat(json(last).has("correlationId")).isTrue();
    }
}
