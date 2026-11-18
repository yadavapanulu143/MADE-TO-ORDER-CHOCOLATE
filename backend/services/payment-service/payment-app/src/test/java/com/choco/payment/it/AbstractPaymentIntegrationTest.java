package com.choco.payment.it;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.domain.port.PaymentGatewayPort;
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
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
 * Base for the payment service's full-stack integration tests.
 *
 * <p>The service's own infrastructure is real: a real PostgreSQL for the Flyway migration, the JPA
 * mapping and the constraints, a real RabbitMQ for the outbox relay and a real Redis. The one thing
 * that is stubbed is the payment provider, because Razorpay cannot be driven deterministically from a
 * test. The stub is a genuine {@link PaymentGatewayPort} implementation that computes real HMAC
 * signatures, so the security-critical path — signature verification — is exercised for real rather
 * than bypassed.
 *
 * <p>Authentication is local for the same reason as in the other services: the service verifies a
 * bearer token against a JWKS document, so the test supplies its own signing key and a matching
 * decoder. The token is a real signed RS256 token that the real filter chain verifies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractPaymentIntegrationTest.TestBeans.class)
public abstract class AbstractPaymentIntegrationTest {

    protected static final String KEY_SECRET = "test_key_secret";
    protected static final String WEBHOOK_SECRET = "test_webhook_secret";
    protected static final String PUBLISHABLE_KEY = "rzp_test_abc123";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("choco_payment")
                    .withUsername("choco_payment")
                    .withPassword("choco_payment_pw")
                    .withReuse(true);

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
                    .withReuse(true);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    /** The provider stand-in, shared with the context so a test can drive it. */
    protected static final FakeProvider PROVIDER = new FakeProvider();

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
        registry.add("choco.payment.razorpay.enabled", () -> "false");
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        RSAKey testRsaKey() throws Exception {
            return new RSAKeyGenerator(2048).keyID("payment-it-key").generate();
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

        /** The provider stand-in takes precedence over the real or unconfigured adapter. */
        @Bean
        @Primary
        PaymentGatewayPort testPaymentGateway() {
            return PROVIDER;
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
        PROVIDER.reset();
    }

    protected String customerToken(UUID customerId) {
        return token(customerId, "customer@choco.test", Roles.CUSTOMER, true, true, Set.of());
    }

    protected String adminToken(UUID adminId) {
        return token(adminId, "owner@choco.test", Roles.ADMIN, false, false,
                Set.of(Roles.PERM_PAYMENT_REFUND, Roles.PERM_ORDER_WRITE));
    }

    private String token(UUID subject, String email, String role, boolean emailVerified,
                         boolean mobileVerified, Set<String> permissions) {
        Instant now = Instant.now();
        org.springframework.security.oauth2.jwt.JwtClaimsSet claims =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .id(java.util.UUID.randomUUID().toString())
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
        return send("GET", path, null, token, null);
    }

    protected java.net.http.HttpResponse<String> post(String path, String body, String token) {
        return send("POST", path, body, token, null);
    }

    protected java.net.http.HttpResponse<String> post(String path, String body, String token,
                                                      String idempotencyKey) {
        return send("POST", path, body, token, idempotencyKey);
    }

    /** Posts a webhook with a correctly computed signature over the raw body. */
    protected java.net.http.HttpResponse<String> postWebhook(String rawBody) {
        return postWebhook(rawBody, signWebhook(rawBody));
    }

    protected java.net.http.HttpResponse<String> postWebhook(String rawBody, String signature) {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        URI.create(url("/api/v1/webhooks/razorpay")))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Razorpay-Signature", signature)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8))
                .build();
        try {
            return http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("webhook POST failed", e);
        }
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
                    TRUNCATE TABLE payment_attempts, refunds, disputes, payments,
                        idempotency_record, audit_log, outbox_event
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("could not reset the database", e);
        }
    }

    protected static String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("could not compute an HMAC for the test", e);
        }
    }

    /** The signature the checkout callback would carry. */
    protected static String signCheckout(String providerOrderId, String providerPaymentId) {
        return hmacSha256Hex(providerOrderId + "|" + providerPaymentId, KEY_SECRET);
    }

    protected static String signWebhook(String rawBody) {
        return hmacSha256Hex(rawBody, WEBHOOK_SECRET);
    }

    /**
     * A provider stand-in that behaves like the real one for everything these tests assert on.
     *
     * <p>Signatures are verified with a real HMAC, so a test that forges one is genuinely rejected —
     * the security boundary is exercised, not stubbed out. Only the network is absent.
     */
    protected static final class FakeProvider implements PaymentGatewayPort {

        private final Map<String, ProviderPayment> payments = new ConcurrentHashMap<>();
        private int orderCounter = 0;
        private int refundCounter = 0;

        void reset() {
            payments.clear();
            orderCounter = 0;
            refundCounter = 0;
        }

        /** Registers a payment the service will see when it verifies. */
        void report(String paymentId, String providerOrderId, long amountMinorUnits, String status,
                    String method) {
            payments.put(paymentId, new ProviderPayment(paymentId, providerOrderId, amountMinorUnits, "INR",
                    "captured".equalsIgnoreCase(status), status, method, null, null, null));
        }

        @Override
        public ProviderOrder createOrder(Money amount, String receipt, Map<String, String> notes) {
            orderCounter++;
            return new ProviderOrder("order_fake_" + orderCounter, amount.minorUnits(),
                    amount.currency().getCurrencyCode(), "created");
        }

        @Override
        public ProviderPayment fetchPayment(String providerPaymentId) {
            ProviderPayment payment = payments.get(providerPaymentId);
            if (payment == null) {
                throw new BusinessRuleException(ErrorCode.NOT_FOUND, "no such payment at the provider");
            }
            return payment;
        }

        @Override
        public boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId,
                                               String signature) {
            if (signature == null || signature.isBlank()) {
                return false;
            }
            return hmacSha256Hex(providerOrderId + "|" + providerPaymentId, KEY_SECRET)
                    .equals(signature);
        }

        @Override
        public boolean verifyWebhookSignature(String rawBody, String signature) {
            if (rawBody == null || signature == null || signature.isBlank()) {
                return false;
            }
            return hmacSha256Hex(rawBody, WEBHOOK_SECRET).equals(signature);
        }

        @Override
        public WebhookEvent parseWebhook(String rawBody) {
            try {
                com.fasterxml.jackson.databind.JsonNode root =
                        new ObjectMapper().readTree(rawBody);
                String eventType = root.path("event").asText(null);
                var payment = root.path("payload").path("payment").path("entity");
                var refund = root.path("payload").path("refund").path("entity");
                var dispute = root.path("payload").path("dispute").path("entity");

                String providerPaymentId = text(payment, "id");
                String providerOrderId = text(payment, "order_id");
                Long amount = number(payment, "amount");
                String currency = text(payment, "currency");
                String method = text(payment, "method");
                String failureReason = text(payment, "error_description");
                String providerRefundId = text(refund, "id");
                String providerDisputeId = text(dispute, "id");
                if (providerPaymentId == null) {
                    providerPaymentId = refund.isMissingNode() ? text(dispute, "payment_id")
                            : text(refund, "payment_id");
                }
                if (amount == null) {
                    amount = refund.isMissingNode() ? number(dispute, "amount") : number(refund, "amount");
                }
                if (currency == null) {
                    currency = refund.isMissingNode() ? text(dispute, "currency") : text(refund, "currency");
                }
                return new WebhookEvent(eventType, providerPaymentId, providerOrderId, amount, currency,
                        method, failureReason, providerRefundId, providerDisputeId);
            } catch (Exception e) {
                throw new IllegalStateException("could not parse the webhook body", e);
            }
        }

        private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
            com.fasterxml.jackson.databind.JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }

        private static Long number(com.fasterxml.jackson.databind.JsonNode node, String field) {
            com.fasterxml.jackson.databind.JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asLong();
        }

        @Override
        public ProviderRefund refund(String providerPaymentId, Money amount, Map<String, String> notes) {
            refundCounter++;
            return new ProviderRefund("rfnd_fake_" + refundCounter, providerPaymentId, amount.minorUnits(),
                    amount.currency().getCurrencyCode(), "processed");
        }

        @Override
        public String publishableKey() {
            return PUBLISHABLE_KEY;
        }
    }

    /** Convenience for the many webhook bodies the tests build. */
    protected static Map<String, Object> orderedMap() {
        return new LinkedHashMap<>();
    }
}
