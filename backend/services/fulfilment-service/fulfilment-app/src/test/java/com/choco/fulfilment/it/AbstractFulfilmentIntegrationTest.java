package com.choco.fulfilment.it;

import com.choco.security.principal.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for the fulfilment service's full-stack integration tests.
 *
 * <p>The service's own dependencies are real containers: a real PostgreSQL so the Flyway migration,
 * the JPA mapping and the constraints are exercised, and a real RabbitMQ so the outbox relay and the
 * {@code order.confirmed} consumer are exercised over a real broker. Redis is real too, though this
 * service stores nothing ephemeral today; it is on the classpath through the shared modules and a
 * missing broker there must not be mistaken for a fulfilment fault.
 *
 * <p>The order service is not started. This service does not own the order, and the hand-over arrives
 * as an event, so the tests publish the same envelope the order service's outbox would and assert on
 * what the kitchen did with it. That is the contract that matters: the JSON shape and the effect.
 *
 * <p>Authentication is local: the service verifies a bearer token against a JWKS document, so the test
 * supplies its own signing key and matching decoder. The token is still a real signed RS256 token that
 * the real filter chain verifies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractFulfilmentIntegrationTest.TestBeans.class)
public abstract class AbstractFulfilmentIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("choco_fulfilment")
                    .withUsername("choco_fulfilment")
                    .withPassword("choco_fulfilment_pw")
                    .withReuse(true);

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
                    .withReuse(true);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        POSTGRES.start();
        RABBITMQ.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("choco.outbox.relay-interval-ms", () -> "200");
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        RSAKey testRsaKey() throws Exception {
            return new RSAKeyGenerator(2048).keyID("fulfilment-it-key").generate();
        }

        @Bean
        JwtEncoder testJwtEncoder(RSAKey testRsaKey) {
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(testRsaKey)));
        }

        @Bean
        @Primary
        JwtDecoder testJwtDecoder(RSAKey testRsaKey) throws Exception {
            return NimbusJwtDecoder.withPublicKey(testRsaKey.toRSAPublicKey()).build();
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtEncoder jwtEncoder;

    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    @org.junit.jupiter.api.BeforeEach
    void resetState() {
        resetDatabase();
    }

    protected String customerToken(UUID customerId) {
        return token(customerId, "customer@choco.test", Roles.CUSTOMER, true, true, Set.of());
    }

    protected String adminToken(UUID adminId) {
        return token(adminId, "owner@choco.test", Roles.ADMIN, false, false,
                Set.of(Roles.PERM_PRODUCTION_WRITE, Roles.PERM_DELIVERY_WRITE, Roles.PERM_SETTINGS_WRITE));
    }

    /** An administrator with no permissions at all, for the authorization tests. */
    protected String unpermissionedAdminToken(UUID adminId) {
        return token(adminId, "staff@choco.test", Roles.ADMIN, false, false, Set.of());
    }

    private String token(UUID subject, String email, String role, boolean emailVerified,
                         boolean mobileVerified, Set<String> permissions) {
        Instant now = Instant.now();
        org.springframework.security.oauth2.jwt.JwtClaimsSet claims =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .subject(subject.toString())
                        .issuer("choco-identity")
                        .issuedAt(now)
                        .expiresAt(now.plus(Duration.ofMinutes(15)))
                        .claim("email", email)
                        .claim("role", role)
                        .claim("email_verified", emailVerified)
                        .claim("mobile_verified", mobileVerified)
                        .claim("permissions", List.copyOf(permissions))
                        .build();
        return jwtEncoder.encode(
                        org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    // ---------------------------------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------------------------------

    protected java.net.http.HttpResponse<String> send(String method, String path, String body,
                                                      String bearerToken) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(
                        URI.create(url(path)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        java.net.http.HttpRequest.BodyPublisher publisher = body == null
                ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        builder.method(method, publisher);
        try {
            return http.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("HTTP " + method + " " + path + " failed", e);
        }
    }

    protected java.net.http.HttpResponse<String> get(String path, String token) {
        return send("GET", path, null, token);
    }

    protected java.net.http.HttpResponse<String> post(String path, String body, String token) {
        return send("POST", path, body, token);
    }

    protected java.net.http.HttpResponse<String> put(String path, String body, String token) {
        return send("PUT", path, body, token);
    }

    protected com.fasterxml.jackson.databind.JsonNode json(java.net.http.HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("response was not JSON: " + response.body(), e);
        }
    }

    protected String jsonBody(Map<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the request body", e);
        }
    }

    protected void resetDatabase() {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE production_notes, production_jobs, recipe_lines, ingredients,
                        stock_ledger_entries, delivery_attempts, deliveries, serviceable_areas,
                        audit_log, outbox_event
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("could not reset the database", e);
        }
    }
}
