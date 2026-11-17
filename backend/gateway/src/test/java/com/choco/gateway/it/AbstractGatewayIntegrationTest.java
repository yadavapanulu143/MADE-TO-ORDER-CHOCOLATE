package com.choco.gateway.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for the gateway's full-stack integration tests.
 *
 * <p>Two real collaborators are used rather than mocks:
 * <ul>
 *   <li><b>Redis</b>, as a container. The edge rate limiter's correctness depends on Redis's atomic
 *       {@code INCR}/{@code EXPIRE}, which an in-memory fake would not reproduce.</li>
 *   <li><b>A JWKS endpoint</b>, served by MockWebServer, holding a freshly generated RSA key. The
 *       gateway's {@code JwtDecoder} fetches this over real HTTP, so token verification is exercised
 *       end to end — a signature mismatch, an unknown key id or an unreachable JWKS would all fail
 *       here exactly as they would in production. Minting the tokens with the matching private key
 *       lets a test assert the difference between a valid token and a forged one.</li>
 * </ul>
 *
 * <p>Downstream services are MockWebServer instances started by each test class, so routing,
 * header propagation and the circuit breaker are exercised without any real backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractGatewayIntegrationTest {

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    /** The signing key the gateway will fetch the public half of, via the JWKS endpoint. */
    protected static final RSAKey SIGNING_KEY = generateKey();

    /** Serves the JWKS document the gateway's decoder fetches. */
    protected static final MockWebServer JWKS_SERVER = new MockWebServer();

    static {
        REDIS.start();
        startJwksServer();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID("choco-identity-1")
                    .generate();
        } catch (Exception e) {
            throw new IllegalStateException("could not generate the test signing key", e);
        }
    }

    private static void startJwksServer() {
        try {
            JWKS_SERVER.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    // The decoder may re-fetch; always answer the same document.
                    String body = new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(body);
                }
            });
            JWKS_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the JWKS server", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("choco.security.jwk-set-uri",
                () -> JWKS_SERVER.url("/.well-known/jwks.json").toString());

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("choco.security.issuer", () -> "choco-identity");

        // The edge throttle is configured per test through a lower allowance, so the default window
        // is kept short to make the tests fast without disabling the control.
        registry.add("choco.gateway.rate-limit.window", () -> "PT1M");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Mints tokens signed by the same key the gateway verifies against. */
    protected final JwtEncoder jwtEncoder = new NimbusJwtEncoder(
            new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY)));

    @BeforeEach
    void resetRedis() {
        flushRedis();
    }

    private void flushRedis() {
        try (var socket = new java.net.Socket(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            var out = socket.getOutputStream();
            out.write("FLUSHALL\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("could not flush Redis", e);
        }
    }

    // -------------------------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------------------------

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected HttpResponse<String> send(String method, String path, String body, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        builder.method(method, publisher);
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("HTTP " + method + " " + path + " failed", e);
        }
    }

    protected HttpResponse<String> get(String path, String token) {
        return send("GET", path, null, token);
    }

    protected HttpResponse<String> post(String path, String body, String token) {
        return send("POST", path, body, token);
    }

    protected JsonNode json(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("response was not JSON: " + response.body(), e);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------------------------

    /** A valid customer access token. */
    protected String customerToken() {
        return token("CUSTOMER", List.of(), true, true);
    }

    /** A valid administrator access token holding the catalogue permission. */
    protected String adminToken() {
        return token("ADMIN", List.of("product:write"), false, false);
    }

    protected String token(String role, List<String> permissions, boolean emailVerified,
                           boolean mobileVerified) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("choco-identity")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .subject(java.util.UUID.randomUUID().toString())
                .claim("role", role)
                .claim("email", "user@choco.test")
                .claim("email_verified", emailVerified)
                .claim("mobile_verified", mobileVerified)
                .claim("permissions", permissions)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** A structurally valid token signed by a different key, so verification must reject it. */
    protected String forgedToken() {
        try {
            RSAKey other = new RSAKeyGenerator(2048).keyID("attacker").generate();
            JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(other)));
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("choco-identity")
                    .issuedAt(now)
                    .expiresAt(now.plus(Duration.ofMinutes(15)))
                    .subject(java.util.UUID.randomUUID().toString())
                    .claim("role", "SUPER_ADMIN")
                    .build();
            return attacker.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        } catch (Exception e) {
            throw new IllegalStateException("could not mint a forged token", e);
        }
    }

    /** An expired token that is otherwise well-formed and correctly signed. */
    protected String expiredToken() {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("choco-identity")
                .issuedAt(past)
                .expiresAt(past.plus(Duration.ofMinutes(1)))
                .subject(java.util.UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** A token signed by the right key but with the wrong issuer. */
    protected String wrongIssuerToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("some-other-issuer")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .subject(java.util.UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    // -------------------------------------------------------------------------------------------
    // Downstream stub helpers
    // -------------------------------------------------------------------------------------------

    /**
     * A downstream stub that answers every request with the given status and JSON body and echoes the
     * received path and headers back, so a test can assert what the gateway forwarded.
     */
    protected MockWebServer downstreamStub() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Map<String, Object> echo = new java.util.LinkedHashMap<>();
                echo.put("path", request.getPath());
                echo.put("method", request.getMethod());
                echo.put("authorization", request.getHeader("Authorization"));
                echo.put("correlationId", request.getHeader("X-Correlation-Id"));
                String body;
                try {
                    body = objectMapper.writeValueAsString(echo);
                } catch (Exception e) {
                    body = "{}";
                }
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(body);
            }
        });
        try {
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the downstream stub", e);
        }
        return server;
    }

    protected void stopQuietly(MockWebServer server) {
        try {
            server.shutdown();
        } catch (IOException e) {
            // The test already finished; a shutdown failure must not mask its result.
        }
    }

    protected static void sleepMillis(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
