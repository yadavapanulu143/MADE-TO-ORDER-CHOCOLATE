package com.choco.catalog.it;

import com.choco.security.principal.Roles;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
 * Base for the catalog service's full-stack integration tests.
 *
 * <p>Every external dependency the service actually uses is real and started as a container: a real
 * PostgreSQL for the Flyway migrations, the JPA mapping and the constraints, and a real RabbitMQ for
 * the outbox relay. The point is to exercise the paths a mocked unit test cannot reach — a migration
 * that does not match its entity, a constraint the application layer does not know about, an event
 * that never leaves the outbox.
 *
 * <p>Authentication is the one piece that is not a live dependency. The service verifies a bearer
 * token against a JWKS document it fetches over HTTP from the identity service; standing up the whole
 * identity service for each catalog test would slow the suite without testing anything about the
 * catalogue. Instead the test supplies its own signing key and the matching decoder, so a token is
 * still a real signed RS256 token that the real filter chain verifies — only the key's origin is
 * local. The claims minted here mirror exactly what the identity service puts in a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractCatalogIntegrationTest.TestBeans.class)
public abstract class AbstractCatalogIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("choco_catalog")
                    .withUsername("choco_catalog")
                    .withPassword("choco_catalog_pw")
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

        // A fast relay tick so a test can observe a published message without a multi-second wait.
        registry.add("choco.outbox.relay-interval-ms", () -> "200");

        // Images are written to a per-run temporary directory so a test never depends on, or leaves
        // behind, a directory in the working tree.
        registry.add("choco.catalog.image-storage.root", () -> IMAGE_ROOT.toString());
    }

    protected static final java.nio.file.Path IMAGE_ROOT =
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "choco-catalog-it-" + UUID.randomUUID());

    /**
     * The signing key and the encoder/decoder pair for the test tokens.
     *
     * <p>A single RSA key is generated once per test context. {@code @ConditionalOnMissingBean} on the
     * shipping {@code jwtDecoder} means this decoder replaces it, so the filter chain verifies the
     * test token exactly as it would verify a real one.
     */
    @TestConfiguration
    static class TestBeans {

        @Bean
        RSAKey testRsaKey() throws Exception {
            return new RSAKeyGenerator(2048).keyID("catalog-it-key").generate();
        }

        @Bean
        JwtEncoder testJwtEncoder(RSAKey testRsaKey) {
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(testRsaKey)));
        }

        @Bean
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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    @org.junit.jupiter.api.BeforeEach
    void resetState() {
        resetDatabase();
    }

    // ---------------------------------------------------------------------------------------------
    // Token minting
    // ---------------------------------------------------------------------------------------------

    /** A customer token with both channels verified, as the identity service would issue it. */
    protected String customerToken(UUID customerId) {
        return token(customerId, "customer@choco.test", Roles.CUSTOMER, true, true, Set.of());
    }

    /** An administrator token holding the catalogue permission. */
    protected String adminToken(UUID adminId) {
        return token(adminId, "owner@choco.test", Roles.ADMIN, false, false,
                Set.of(Roles.PERM_PRODUCT_WRITE));
    }

    /** An administrator token that deliberately lacks the catalogue permission. */
    protected String adminWithoutCatalogPermission(UUID adminId) {
        return token(adminId, "reporter@choco.test", Roles.ADMIN, false, false,
                Set.of(Roles.PERM_REPORT_READ));
    }

    /** A super-administrator token holding every permission, as the identity service would issue it. */
    protected String superAdminToken(UUID adminId) {
        return token(adminId, "owner@choco.test", Roles.SUPER_ADMIN, false, false,
                Set.of(Roles.PERM_CUSTOMER_EXPORT, Roles.PERM_PRODUCT_WRITE, Roles.PERM_ORDER_WRITE,
                        Roles.PERM_PAYMENT_REFUND, Roles.PERM_DELIVERY_WRITE, Roles.PERM_PRODUCTION_WRITE,
                        Roles.PERM_REPORT_READ, Roles.PERM_SETTINGS_WRITE, Roles.PERM_ADMIN_MANAGE));
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

    protected HttpResponse<String> send(String method, String path, String body, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(15))
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

    protected HttpResponse<String> post(String path, String body, String token) {
        return send("POST", path, body, token);
    }

    protected HttpResponse<String> get(String path, String token) {
        return send("GET", path, null, token);
    }

    protected HttpResponse<String> put(String path, String body, String token) {
        return send("PUT", path, body, token);
    }

    protected HttpResponse<String> delete(String path, String token) {
        return send("DELETE", path, null, token);
    }

    protected JsonNode json(HttpResponse<String> response) {
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

    // ---------------------------------------------------------------------------------------------
    // State management
    // ---------------------------------------------------------------------------------------------

    /**
     * Empties every table between tests, leaving the schema in place so Flyway runs once per class and
     * the JPA mapping is validated against the same schema every time.
     */
    protected void resetDatabase() {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE product_variants, product_images, product_attributes, products,
                        website_settings, audit_log, outbox_event
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("could not reset the database", e);
        }
    }

    /** Creates a published product with one variant and returns its id, ready to be ordered. */
    protected String createPublishedProduct(String token, String name, String price) {
        HttpResponse<String> created = post("/api/v1/admin/catalog/products",
                jsonBody(Map.of("name", name, "shortDescription", "70% cocoa", "description", "Rich and dark",
                        "preparationInfo", "Made fresh to order", "deliveryInfo", "Delivered in 3 days")),
                token);
        String id = json(created).get("id").asText();
        post("/api/v1/admin/catalog/products/" + id + "/variants",
                jsonBody(Map.of("label", "200 g", "sku", "SKU-" + UUID.randomUUID(), "price", price,
                        "minOrderQuantity", 1, "maxOrderQuantity", 10)),
                token);
        post("/api/v1/admin/catalog/products/" + id + "/publish", null, token);
        return id;
    }
}
