package com.choco.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * HSTS must be sent when the request reached the application over HTTPS, and only then.
 *
 * <p>Behind a TLS-terminating reverse proxy every request arrives over plain HTTP, so Spring
 * Security's default {@code isSecure()} matcher would never emit {@code Strict-Transport-Security} and
 * the header would silently do nothing in production. The gateway is configured to trust
 * {@code X-Forwarded-Proto} from a configured proxy, and this test starts the gateway with
 * {@code 127.0.0.1} as that proxy so the forwarded scheme is honoured — the same address the test's
 * client connects from, which is exactly how a co-located proxy appears.
 */
class GatewayHstsIT extends AbstractGatewayIntegrationTest {

    @DynamicPropertySource
    static void trustTheLoopbackProxy(DynamicPropertyRegistry registry) {
        registry.add("choco.http.trusted-proxies", () -> "127.0.0.1");
    }

    @Test
    @DisplayName("a request a trusted proxy marks https carries HSTS")
    void forwardedHttpsCarriesHsts() {
        HttpResponse<String> response = getWithScheme("/api/v1/orders/mine", "https");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Strict-Transport-Security"))
                .as("a proxy-terminated HTTPS request must be pinned")
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("max-age=");
    }

    @Test
    @DisplayName("a plain-HTTP request through the same proxy does not carry HSTS")
    void forwardedHttpDoesNotCarryHsts() {
        HttpResponse<String> response = getWithScheme("/api/v1/orders/mine", "http");

        // Emitting HSTS here would pin the scheme for a host that never served HTTPS, which can lock
        // a development machine out of the site until the max-age expires.
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    private HttpResponse<String> getWithScheme(String path, String scheme) {
        var http = java.net.http.HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(20))
                .header("X-Forwarded-Proto", scheme)
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
