package com.choco.order.it;

import com.choco.order.adapter.out.client.DownstreamProperties;
import com.choco.security.principal.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Base for the order service's full-stack integration tests.
 *
 * <p>The order service's own dependencies are real containers: a real PostgreSQL for the Flyway
 * migration, the JPA mapping and the constraints, a real RabbitMQ for the outbox relay, and a real
 * Redis. Its <em>downstream</em> services are stubbed with an in-process HTTP server rather than
 * containers. That is deliberate: the order service does not own the catalogue or identity, so what
 * these tests must prove is that the order service speaks the agreed HTTP contract correctly — the
 * request it sends and how it reacts to each answer — not that another service works. A stub also
 * lets a test force the awkward answers, a 404 or a 503, that a real service is hard to make produce
 * on demand.
 *
 * <p>Authentication is local for the same reason as in the catalog tests: the service verifies a
 * bearer token against a JWKS document, so the test supplies its own signing key and matching decoder.
 * A token is still a real signed RS256 token that the real filter chain verifies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractOrderIntegrationTest.TestBeans.class)
public abstract class AbstractOrderIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("choco_order")
                    .withUsername("choco_order")
                    .withPassword("choco_order_pw")
                    .withReuse(true);

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
                    .withReuse(true);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    /** The downstream stand-in. One server answers for catalogue, identity and delivery. */
    protected static final DownstreamStub DOWNSTREAM = new DownstreamStub();

    static {
        POSTGRES.start();
        RABBITMQ.start();
        REDIS.start();
        DOWNSTREAM.start();
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

        // Point every downstream port at the one stub server.
        registry.add("choco.order.downstream.catalog", DOWNSTREAM::baseUrl);
        registry.add("choco.order.downstream.identity", DOWNSTREAM::baseUrl);
        registry.add("choco.order.downstream.fulfilment", DOWNSTREAM::baseUrl);
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        RSAKey testRsaKey() throws Exception {
            return new RSAKeyGenerator(2048).keyID("order-it-key").generate();
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
        DOWNSTREAM.reset();
    }

    protected String customerToken(UUID customerId) {
        return token(customerId, "customer@choco.test", Roles.CUSTOMER, true, true, Set.of());
    }

    protected String adminToken(UUID adminId) {
        return token(adminId, "owner@choco.test", Roles.ADMIN, false, false,
                Set.of(Roles.PERM_ORDER_WRITE, Roles.PERM_PRODUCTION_WRITE, Roles.PERM_DELIVERY_WRITE));
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
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    // ---------------------------------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------------------------------

    protected java.net.http.HttpResponse<String> send(String method, String path, String body,
                                                      String bearerToken) {
        return send(method, path, body, bearerToken, null);
    }

    protected java.net.http.HttpResponse<String> send(String method, String path, String body,
                                                      String bearerToken, String idempotencyKey) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
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
                    TRUNCATE TABLE order_lines, order_status_history, order_notes, orders,
                        cart_lines, carts, idempotency_record, audit_log, outbox_event
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("could not reset the database", e);
        }
    }

    protected DownstreamProperties properties() {
        DownstreamProperties properties = new DownstreamProperties();
        properties.setCatalog(URI.create(DOWNSTREAM.baseUrl()));
        properties.setIdentity(URI.create(DOWNSTREAM.baseUrl()));
        properties.setFulfilment(URI.create(DOWNSTREAM.baseUrl()));
        return properties;
    }

    // ---------------------------------------------------------------------------------------------
    // Downstream stand-in
    // ---------------------------------------------------------------------------------------------

    /**
     * An in-process HTTP server standing in for the catalogue, identity and delivery services.
     *
     * <p>Records the last request path and query so a test can assert on the contract the order
     * service actually sent, and lets a test force a specific response — including a 404 or a 503 —
     * that a real service is awkward to make produce on demand.
     */
    protected static final class DownstreamStub {

        private final HttpServer server;
        private final Map<String, StubbedResponse> routes = new ConcurrentHashMap<>();
        private final AtomicInteger requests = new AtomicInteger();
        private volatile String lastPath;
        private volatile String lastQuery;
        private volatile String lastAuthorization;

        DownstreamStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", this::handle);
            } catch (IOException e) {
                throw new IllegalStateException("could not start the downstream stub", e);
            }
        }

        void start() {
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** Registers a fixed response for a path prefix. */
        DownstreamStub on(String pathPrefix, int status, String jsonBody) {
            routes.put(pathPrefix, new StubbedResponse(status, jsonBody));
            return this;
        }

        void reset() {
            routes.clear();
            requests.set(0);
            lastPath = null;
            lastQuery = null;
            lastAuthorization = null;
        }

        int requestCount() {
            return requests.get();
        }

        String lastPath() {
            return lastPath;
        }

        String lastQuery() {
            return lastQuery;
        }

        String lastAuthorization() {
            return lastAuthorization;
        }

        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            lastPath = exchange.getRequestURI().getPath();
            lastQuery = exchange.getRequestURI().getQuery();
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");

            StubbedResponse response = null;
            for (Map.Entry<String, StubbedResponse> entry : routes.entrySet()) {
                if (lastPath.startsWith(entry.getKey())) {
                    response = entry.getValue();
                    break;
                }
            }
            if (response == null) {
                write(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                return;
            }
            write(exchange, response.status(), response.body());
        }

        private void write(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        private record StubbedResponse(int status, String body) {
        }
    }
}
