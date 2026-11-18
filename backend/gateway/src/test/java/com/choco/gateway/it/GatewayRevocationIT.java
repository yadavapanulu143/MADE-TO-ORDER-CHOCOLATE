package com.choco.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.core.port.TokenRevocationRegistry;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves that a revoked access token is refused at the edge, before any downstream call.
 *
 * <p>This is the boundary the admin-session security work turns on. Token revocation lives in the
 * identity service's logout, refresh-rotation and replay paths and is stored in Redis; every service
 * reads it through the shared {@code RevocationAwareJwtDecoder}. The gateway is the first hop for a
 * browser, so if the edge did not consult the store, a "logged out" administrator's token would still
 * open every administrative route for the rest of its lifetime — the exact gap the finding describes.
 *
 * <p>The test drives the real registry against the real Redis container and asserts on the gateway's
 * answer, so it fails if the decoder is ever swapped for a plain {@code NimbusJwtDecoder}, if the
 * gateway stops depending on {@code common-redis}, or if a route stops being protected.
 */
class GatewayRevocationIT extends AbstractGatewayIntegrationTest {

    /** Every route is pointed at one stub: the tests assert the gateway's decision, not the backend. */
    private static final MockWebServer DOWNSTREAM = downstreamStubStatic();

    private static MockWebServer downstreamStubStatic() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public okhttp3.mockwebserver.MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                return new okhttp3.mockwebserver.MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"path\":\"" + request.getPath() + "\"}");
            }
        });
        try {
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException("could not start the downstream stub", e);
        }
        return server;
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = DOWNSTREAM.url("/").toString();
        for (String service : List.of("identity", "catalog", "order", "payment", "fulfilment",
                "notification", "reporting")) {
            registry.add("choco.gateway.services." + service, () -> url);
        }
    }

    @AfterAll
    static void stop() throws Exception {
        DOWNSTREAM.shutdown();
    }

    @Autowired
    private TokenRevocationRegistry revocationRegistry;

    /** The customer profile path is protected and routes to identity. */
    private static final String CUSTOMER_PATH = "/api/v1/customers/me";

    /** An administrative path, protected and routed to the notification service. */
    private static final String ADMIN_PATH = "/api/v1/admin/notifications";

    private String tokenWith(String role, List<String> permissions, UUID subject, String sessionId,
                             Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(sessionId)
                .issuer("choco-identity")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .subject(subject.toString())
                .claim("role", role)
                .claim("email", "user@choco.test")
                .claim("email_verified", true)
                .claim("mobile_verified", true)
                .claim("permissions", permissions)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String customerToken(UUID subject, String sessionId, Instant issuedAt) {
        return tokenWith("CUSTOMER", List.of(), subject, sessionId, issuedAt);
    }

    private String adminToken(UUID subject, String sessionId, Instant issuedAt) {
        return tokenWith("ADMIN", List.of("notification:read"), subject, sessionId, issuedAt);
    }

    @Test
    @DisplayName("a live token is accepted at the edge and forwarded")
    void liveTokenIsForwarded() {
        String token = customerToken(UUID.randomUUID(), UUID.randomUUID().toString(), Instant.now());

        HttpResponse<String> response = get(CUSTOMER_PATH, token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo(CUSTOMER_PATH);
    }

    @Test
    @DisplayName("a token whose session was revoked is refused before any downstream call")
    void revokedSessionIsRefused() {
        UUID subject = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        String token = customerToken(subject, sessionId, Instant.now());

        // The customer signs out: the identity service retires the session.
        revocationRegistry.revokeSession(sessionId, Instant.now().plus(Duration.ofMinutes(15)));

        int before = DOWNSTREAM.getRequestCount();
        HttpResponse<String> response = get(CUSTOMER_PATH, token);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(DOWNSTREAM.getRequestCount())
                .as("a refused request must never reach the service")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("a logged-out administrator cannot reach an administrative route")
    void revokedAdminSessionCannotReachAdminRoute() {
        UUID subject = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        String token = adminToken(subject, sessionId, Instant.now());

        assertThat(get(ADMIN_PATH, token).statusCode())
                .as("the token works before logout")
                .isEqualTo(200);

        revocationRegistry.revokeSession(sessionId, Instant.now().plus(Duration.ofMinutes(15)));

        assertThat(get(ADMIN_PATH, token).statusCode())
                .as("the same token must be refused once the session is revoked")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("revoking one session does not touch another")
    void revocationIsScopedToTheSession() {
        UUID subject = UUID.randomUUID();
        String revokedSession = UUID.randomUUID().toString();
        String liveSession = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String revoked = customerToken(subject, revokedSession, now);
        String live = customerToken(subject, liveSession, now);

        revocationRegistry.revokeSession(revokedSession, now.plus(Duration.ofMinutes(15)));

        assertThat(get(CUSTOMER_PATH, revoked).statusCode()).isEqualTo(401);
        assertThat(get(CUSTOMER_PATH, live).statusCode())
                .as("a second, still-signed-in session must keep working")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("a subject cutoff refuses tokens issued before it and accepts one issued after")
    void subjectCutoffRefusesOnlyOlderTokens() {
        UUID subject = UUID.randomUUID();
        String olderSession = UUID.randomUUID().toString();
        String older = customerToken(subject, olderSession, Instant.now());

        // A password change retires every token issued before the change.
        revocationRegistry.revokeSubjectIssuedBefore(subject, Instant.now().plus(Duration.ofSeconds(60)),
                Instant.now().plus(Duration.ofMinutes(15)));
        assertThat(get(CUSTOMER_PATH, older).statusCode())
                .as("a token issued before the cutoff is refused")
                .isEqualTo(401);

        // A fresh sign-in after the change issues a token at the new time, which must work.
        String fresh = customerToken(subject, UUID.randomUUID().toString(), Instant.now());
        revocationRegistry.revokeSubjectIssuedBefore(subject, Instant.now().minus(Duration.ofSeconds(60)),
                Instant.now().plus(Duration.ofMinutes(15)));
        assertThat(get(CUSTOMER_PATH, fresh).statusCode())
                .as("a token issued after the cutoff is accepted")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("an unauthenticated request to an administrative route is refused")
    void unauthenticatedAdminRouteIsRefused() {
        int before = DOWNSTREAM.getRequestCount();
        HttpResponse<String> response = get(ADMIN_PATH, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(DOWNSTREAM.getRequestCount())
                .as("a refused request must never reach the service")
                .isEqualTo(before);
    }
}
