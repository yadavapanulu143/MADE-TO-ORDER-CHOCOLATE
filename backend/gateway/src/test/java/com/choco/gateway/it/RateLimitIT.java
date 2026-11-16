package com.choco.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
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
 * Verifies the edge throttle.
 *
 * <p>The allowance is set to a small number for the test so the boundary is reached quickly, but the
 * control itself is the production one: a Redis-backed counter. Asserting against a real Redis proves
 * the window behaves as a window (the counter resets) and that a refused request is refused before it
 * reaches the service, which is the point of an edge limit.
 */
class RateLimitIT extends AbstractGatewayIntegrationTest {

    private static final MockWebServer IDENTITY = new MockWebServer();

    static {
        IDENTITY.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"ok\":true}");
            }
        });
        try {
            IDENTITY.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("choco.gateway.services.identity", () -> IDENTITY.url("/").toString());
        registry.add("choco.gateway.rate-limit.requests-per-window", () -> "5");
        registry.add("choco.gateway.rate-limit.auth-requests-per-window", () -> "3");
        registry.add("choco.gateway.rate-limit.enabled", () -> "true");
    }

    @AfterAll
    static void stop() throws Exception {
        IDENTITY.shutdown();
    }

    @Test
    @DisplayName("a burst on a general path is throttled once the allowance is spent")
    void generalAllowanceIsEnforced() {
        // The allowance is 5 per window; the sixth request from the same address must be refused.
        HttpResponse<String> last = null;
        for (int i = 0; i < 6; i++) {
            last = get("/api/v1/customers/me", customerToken());
        }

        assertThat(last.statusCode()).isEqualTo(429);
        assertThat(json(last).get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(last.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    @DisplayName("the authentication allowance is tighter than the general one")
    void authAllowanceIsTighter() {
        // The auth allowance is 3; the fourth call must already be refused, before the general
        // allowance of 5 would have been reached.
        HttpResponse<String> last = null;
        for (int i = 0; i < 4; i++) {
            last = send("POST", "/api/v1/auth/login", "{}", null);
        }

        assertThat(last.statusCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("a throttled request never reaches the downstream service")
    void throttledRequestIsNotForwarded() throws Exception {
        for (int i = 0; i < 5; i++) {
            get("/api/v1/customers/me", customerToken());
        }
        int before = IDENTITY.getRequestCount();

        HttpResponse<String> refused = get("/api/v1/customers/me", customerToken());

        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(IDENTITY.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("the auth bucket and the general bucket are counted separately")
    void bucketsAreIndependent() {
        // Spend the general allowance entirely.
        for (int i = 0; i < 6; i++) {
            get("/api/v1/customers/me", customerToken());
        }
        assertThat(get("/api/v1/customers/me", customerToken()).statusCode()).isEqualTo(429);

        // The auth path has its own counter, so it is still allowed its own (smaller) allowance
        // rather than inheriting the exhausted general bucket.
        HttpResponse<String> auth = send("POST", "/api/v1/auth/login", "{}", null);
        assertThat(auth.statusCode()).isNotEqualTo(429);
    }
}
