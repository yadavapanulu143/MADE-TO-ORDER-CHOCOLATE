package com.choco.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
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
 * Verifies that the circuit breaker does not impose its own, tighter, timeout on a healthy service.
 *
 * <p>Spring Cloud CircuitBreaker's Resilience4j default wraps every proxied call in a
 * {@code TimeLimiter} whose default is <b>one second</b> with {@code cancelRunningFuture=true}. On
 * this blocking gateway that silently cancelled any downstream response that legitimately took
 * longer than a second — a cold JVM's first request, or Argon2 password hashing during registration,
 * which is deliberately slow — and answered {@code 503} from the fallback even though the service was
 * healthy and had in fact completed the work. The bound that is meant to apply is the proxy read
 * timeout, which is configurable and an order of magnitude larger.
 *
 * <p>The stub delays the body by a different amount per path, so the two cases are distinguished by
 * where the request was routed rather than by a header that could be filtered in transit.
 */
class GatewayCircuitBreakerTimeoutIT extends AbstractGatewayIntegrationTest {

    /** The proxy read timeout under test. Chosen small so the suite stays fast. */
    private static final String READ_TIMEOUT = "2s";

    private static final MockWebServer CATALOG = new MockWebServer();

    static {
        CATALOG.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                // The delay must happen before the response starts, exactly as a slow handler does:
                // Argon2 password hashing delays the status line, not the body. A body-only delay
                // happens after the headers are sent and so falls outside the guarded call, which is
                // why it does not reproduce the defect.
                long delayMillis = path.contains("slow-within") ? 1500L : 3000L;
                sleep(delayMillis);
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
        registry.add("spring.http.client.read-timeout", () -> READ_TIMEOUT);
        registry.add("choco.gateway.rate-limit.enabled", () -> "false");
    }

    @AfterAll
    static void stop() throws Exception {
        CATALOG.shutdown();
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("a healthy service slower than the old one-second limit is delivered, not 503")
    void slowButWithinReadTimeoutIsDelivered() {
        HttpResponse<String> response = get("/api/v1/products/slow-within", null);
        assertThat(response.statusCode())
                .as("a 1.5s response is inside the configured 2s read timeout and must be proxied")
                .isEqualTo(200);
        assertThat(json(response).get("ok").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a response beyond the configured read timeout is still refused, so the route stays bounded")
    void beyondReadTimeoutIsRefused() {
        HttpResponse<String> response = get("/api/v1/products/slow-beyond", null);
        assertThat(response.statusCode())
                .as("a 3s response exceeds the 2s read timeout and must be refused, not reported as success")
                .isEqualTo(503);
    }
}
