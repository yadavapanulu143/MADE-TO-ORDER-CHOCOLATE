package com.choco.reporting.it;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventEnvelope;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
 * Base for the reporting service's full-stack integration tests.
 *
 * <p>What is real: a real PostgreSQL so the Flyway migration, the primary keys and the JPA mapping are
 * exercised; a real RabbitMQ so a published event is actually routed to this service's queue and
 * projected; and a real Redis, because the shared modules are on the classpath. No other service is
 * started — the reporting service owns nothing they do and hears from them only through events, so the
 * tests publish the same envelopes their outboxes would and assert on what the read model became.
 *
 * <p>Authentication is local: the service verifies a bearer token against a JWKS document, so the test
 * supplies its own signing key and a matching decoder, and the token is a real signed RS256 token the
 * real filter chain verifies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractReportingIntegrationTest.TestBeans.class)
public abstract class AbstractReportingIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("choco_reporting")
                    .withUsername("choco_reporting")
                    .withPassword("choco_reporting_pw")
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
            return new RSAKeyGenerator(2048).keyID("reporting-it-key").generate();
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

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void resetState() {
        resetDatabase();
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * Publishes an event the way a producer's outbox relay would, so the consumer's binding, header
     * handling and payload parsing are all exercised rather than assumed.
     */
    protected void publish(String eventType, String aggregateId, Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.put("eventId", UUID.randomUUID().toString());
        body.put("occurredAt", Instant.now().toString());
        body.put("aggregateId", aggregateId);

        EventEnvelope envelope = EventEnvelope.of(UUID.randomUUID(), eventType, 1, aggregateId,
                Instant.now(), null, "it-producer", body);
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the test event", e);
        }
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, eventType, json, message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setContentEncoding("UTF-8");
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setHeader("eventType", eventType);
            return message;
        });
    }

    protected String adminToken(UUID adminId) {
        return token(adminId, "owner@choco.test", Roles.ADMIN,
                Set.of(Roles.PERM_REPORT_READ, Roles.PERM_CUSTOMER_EXPORT));
    }

    /** An administrator who may read reports but not export customers. */
    protected String reportOnlyAdminToken(UUID adminId) {
        return token(adminId, "analyst@choco.test", Roles.ADMIN, Set.of(Roles.PERM_REPORT_READ));
    }

    protected String customerToken(UUID customerId) {
        return token(customerId, "customer@choco.test", Roles.CUSTOMER, Set.of());
    }

    private String token(UUID subject, String email, String role, Set<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject.toString())
                .issuer("choco-identity")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("email", email)
                .claim("role", role)
                .claim("email_verified", true)
                .claim("mobile_verified", true)
                .claim("permissions", List.copyOf(permissions))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

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
        var publisher = body == null
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

    protected com.fasterxml.jackson.databind.JsonNode json(java.net.http.HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("response was not JSON: " + response.body(), e);
        }
    }

    protected void resetDatabase() {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE order_projection, payment_projection, production_projection,
                        delivery_projection, refund_projection, customer_projection,
                        invoice_projection, audit_log, outbox_event
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("could not reset the database", e);
        }
    }
}
