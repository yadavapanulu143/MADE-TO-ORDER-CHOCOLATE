package com.choco.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;

/**
 * Edge security behaviour that is easy to get wrong and dangerous when it is: which paths are open,
 * what the error body looks like, and which origins may call the API.
 */
class GatewaySecurityIT extends AbstractGatewayIntegrationTest {

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
        registry.add("choco.gateway.rate-limit.enabled", () -> "false");
        registry.add("choco.cors.allowed-origins", () -> "http://localhost:5173");
    }

    @AfterAll
    static void stop() throws Exception {
        IDENTITY.shutdown();
    }


    @Test
    @DisplayName("registration is reachable without a token")
    void registrationIsPublic() {
        HttpResponse<String> response = send("POST", "/api/v1/auth/register",
                "{\"email\":\"a@b.test\"}", null);

        // It must reach the service (which will reject the body); the point is that the edge did not
        // answer 401 before the service could.
        assertThat(response.statusCode()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("the JWKS document is public so other services can verify tokens")
    void jwksIsPublic() {
        HttpResponse<String> response = get("/.well-known/jwks.json", null);

        assertThat(response.statusCode()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("a customer profile path is never public")
    void customerProfileIsProtected() {
        HttpResponse<String> response = get("/api/v1/customers/me", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json(response).get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("a 401 carries a problem-detail body, not an empty one")
    void unauthorizedBodyIsProblemDetail() {
        HttpResponse<String> response = get("/api/v1/orders/mine", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
        assertThat(json(response).get("status").asInt()).isEqualTo(401);
        assertThat(json(response).get("instance").asText()).isEqualTo("/api/v1/orders/mine");
    }

    @Test
    @DisplayName("a preflight from an allowed origin is answered with that origin")
    void preflightFromAllowedOrigin() {
        HttpResponse<String> response = preflight("http://localhost:5173");

        assertThat(response.statusCode()).isIn(200, 204);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
    }

    @Test
    @DisplayName("a preflight from an unknown origin is refused, never wildcarded")
    void preflightFromUnknownOrigin() {
        HttpResponse<String> response = preflight("http://evil.example");

        // The allow-list must not degrade to "*". A wildcard with credentials would let any site
        // call the API with the user's token.
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .isNotEqualTo(java.util.Optional.of("*"));
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .isNotEqualTo(java.util.Optional.of("http://evil.example"));
    }

    private HttpResponse<String> preflight(String origin) {
        var http = java.net.http.HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/orders/mine")))
                .timeout(Duration.ofSeconds(20))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
