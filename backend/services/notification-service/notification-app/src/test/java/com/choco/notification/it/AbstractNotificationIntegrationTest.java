package com.choco.notification.it;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventEnvelope;
import com.choco.security.principal.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * Base for the notification service's full-stack integration tests.
 *
 * <p>The service's own dependencies are real: a real PostgreSQL so the Flyway migration, the unique
 * dedupe index and the JPA mapping are exercised; a real RabbitMQ so a published event is actually
 * routed to the consumer's queue and handled; and a real SMTP server (GreenMail) so the email path
 * exercises the genuine {@code JavaMailSender} rather than a mock. Redis is real too, on the
 * classpath through the shared modules.
 *
 * <p>Other services are not started. The notification service owns nothing they do, and their
 * interaction is entirely through events, so the tests publish the same envelopes their outboxes
 * would and assert on what the service sent and recorded. That is the contract that matters: the JSON
 * shape, the routing, and the effect.
 *
 * <p>Authentication is local: the service verifies a bearer token against a JWKS document, so the test
 * supplies its own signing key and a matching decoder. The token is a real signed RS256 token the real
 * filter chain verifies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractNotificationIntegrationTest.TestBeans.class)
public abstract class AbstractNotificationIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("choco_notification")
                    .withUsername("choco_notification")
                    .withPassword("choco_notification_pw")
                    .withReuse(true);

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
                    .withReuse(true);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    /** A fixed SMTP port, for the same reason as the identity suite: the context is cached and reused. */
    protected static final int SMTP_PORT = 4025;

    @RegisterExtension
    protected static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(
            new ServerSetup(SMTP_PORT, null, ServerSetup.PROTOCOL_SMTP))
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(false);

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

        registry.add("spring.mail.host", () -> GREEN_MAIL.getSmtp().getBindTo());
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("management.health.mail.enabled", () -> "false");

        registry.add("choco.notifications.from", () -> "noreply@choco.test");
        registry.add("choco.notifications.seller.email", () -> "seller@choco.test");
        registry.add("choco.notifications.seller.mobile", () -> "9000000000");

        // The scheduler is driven by the tests themselves so a retry is asserted deterministically
        // rather than raced against a timer; the real query and the real repository are still used.
        registry.add("choco.notifications.retry.enabled", () -> "false");

        registry.add("choco.outbox.relay-interval-ms", () -> "200");
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        RSAKey testRsaKey() throws Exception {
            return new RSAKeyGenerator(2048).keyID("notification-it-key").generate();
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
        GREEN_MAIL.reset();
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ---------------------------------------------------------------------------------------------
    // Event publishing
    // ---------------------------------------------------------------------------------------------

    /**
     * Publishes an event the way a producer's outbox relay would, so the consumer's binding, header
     * handling and payload parsing are all exercised.
     */
    protected void publish(String eventType, String aggregateId, Map<String, Object> payload) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(payload);
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

    // ---------------------------------------------------------------------------------------------
    // Tokens
    // ---------------------------------------------------------------------------------------------

    protected String customerToken(UUID customerId) {
        return token(customerId, "customer@choco.test", Roles.CUSTOMER, Set.of());
    }

    protected String adminToken(UUID adminId) {
        return token(adminId, "owner@choco.test", Roles.ADMIN,
                Set.of(Roles.PERM_NOTIFICATION_READ, Roles.PERM_NOTIFICATION_WRITE));
    }

    /** An administrator with neither notification permission, for the authorization tests. */
    protected String unpermissionedAdminToken(UUID adminId) {
        return token(adminId, "staff@choco.test", Roles.ADMIN, Set.of());
    }

    private String token(UUID subject, String email, String role, Set<String> permissions) {
        Instant now = Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
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
        return jwtEncoder.encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims)).getTokenValue();
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

    protected java.net.http.HttpResponse<String> post(String path, String body, String token) {
        return send("POST", path, body, token);
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
                    TRUNCATE TABLE notifications, customer_contacts, audit_log, outbox_event
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("could not reset the database", e);
        }
    }
}
