package com.choco.identity.it;

import com.choco.identity.adapter.out.notification.CapturingSmsConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for the identity service's full-stack integration tests.
 *
 * <p>Every external dependency the service actually uses is a real one, started as a container: a
 * real PostgreSQL for the Flyway migrations, the JPA mapping and the constraints; a real Redis for
 * the rate limiter; a real RabbitMQ for the outbox relay. The point is to exercise the code paths a
 * mocked unit test cannot reach — a migration that does not match its entity, a constraint the
 * application layer does not know about, a Lua script that is not atomic, an event that never leaves
 * the outbox. Those are exactly the failures a green unit-test run hides.
 *
 * <p>Mail is a real SMTP server too (GreenMail), so registration exercises the genuine
 * {@code JavaMailSender} path and a test can read the delivered message to obtain the OTP that would
 * otherwise exist only as a hash in the database. The code is never logged, so this is the only
 * faithful way to complete a registration without weakening the production default.
 *
 * <p>Containers are static and started once for the whole hierarchy, so the suite pays the startup
 * cost once. Each test gets a clean slate through {@link #resetDatabase()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractIdentityIntegrationTest.TestBeans.class)
public abstract class AbstractIdentityIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("choco_identity")
                    .withUsername("choco_identity")
                    .withPassword("choco_identity_pw")
                    .withReuse(true);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
                    .withReuse(true);

    protected static final int SMTP_PORT = 3025;

    /**
     * SMTP sink. The port is fixed rather than random because GreenMail restarts between test
     * classes while the Spring context — and its {@code spring.mail.port} — is cached and reused. A
     * random port would leave the second class's context sending to the first class's dead port.
     * A single reserved port keeps every context pointed at the live sink.
     */
    @RegisterExtension
    protected static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(
            new ServerSetup(SMTP_PORT, null, ServerSetup.PROTOCOL_SMTP))
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(false);

    static {
        POSTGRES.start();
        REDIS.start();
        RABBITMQ.start();
    }

    /**
     * A port discovered free at class load.
     *
     * <p>The access-token decoder validates tokens by fetching this service's own JWKS over HTTP, so
     * the server and the decoder must agree on an address before either starts. A random container
     * port is not enough: the property has to be known when the context is built. Discovering a free
     * port up front keeps the real self-fetch path in the test rather than replacing the decoder.
     */
    private static final int HTTP_PORT = findFreePort();

    private static int findFreePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("could not reserve a port for the test server", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> HTTP_PORT);
        registry.add("choco.security.jwk-set-uri",
                () -> "http://localhost:" + HTTP_PORT + "/.well-known/jwks.json");

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

        registry.add("spring.mail.host", () -> GREEN_MAIL.getSmtp().getBindTo());
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());

        // Codes are read from the SMTP sink, never from the log: the production-safe default is kept
        // in force for the whole suite.
        registry.add("choco.notifications.log-otp", () -> "false");

        // A fast relay tick so a test can observe a published message without a multi-second wait.
        registry.add("choco.outbox.relay-interval-ms", () -> "200");

        registry.add("management.health.mail.enabled", () -> "false");

        // A bootstrap administrator, so the startup runner is exercised against the real database in
        // every test context. The password passes the shared policy; the runner refuses a weak one.
        registry.add("choco.admin.bootstrap.email", () -> BOOTSTRAP_ADMIN_EMAIL);
        registry.add("choco.admin.bootstrap.password", () -> BOOTSTRAP_ADMIN_PASSWORD);
        registry.add("choco.admin.bootstrap.full-name", () -> "Shop Owner");
    }

    protected static final String BOOTSTRAP_ADMIN_EMAIL = "owner@choco.test";
    protected static final String BOOTSTRAP_ADMIN_PASSWORD = "Bootstrap!Pass123";

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Adds the SMS capture to the context.
     *
     * <p>A nested {@code @TestConfiguration} is imported by the base class, which is how the
     * package-private SMS seam can be reached from a test that lives in another package. Its bean
     * overrides the shipping logging sender.
     */
    @TestConfiguration
    @Import(CapturingSmsConfiguration.class)
    static class TestBeans {
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void resetState() {
        prepareEachTest();
    }

    /**
     * Gives each test a clean slate.
     *
     * <p>Overridable so a test that needs a fixture created at context start (the bootstrap
     * administrator, for instance) can recreate it after the truncation instead of duplicating the
     * reset logic.
     */
    protected void prepareEachTest() {
        resetDatabase();
    }

    // ---------------------------------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------------------------------

    /** Sends a JSON request and returns the raw response. */
    protected HttpResponse<String> send(String method, String path, String body, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
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

    protected HttpResponse<String> post(String path, String body) {
        return send("POST", path, body, null);
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

    protected HttpResponse<String> patch(String path, String body, String token) {
        return send("PATCH", path, body, token);
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
     * Empties every table between tests.
     *
     * <p>The tables are truncated rather than the schema dropped, so Flyway runs once per class and
     * the JPA mapping is validated against the same schema for every test. {@code RESTART IDENTITY}
     * also resets the audit sequence, giving each test the same starting point as a fresh database.
     */
    protected void resetDatabase() {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE admin_refresh_tokens, admin_accounts, refresh_tokens,
                        password_reset_tokens, otp_challenges, customer_verified_channels,
                        customers, audit_log, outbox_event, customer_addresses
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("could not reset the database", e);
        }
        flushRedis();
        clearMail();
        CapturingSmsConfiguration.clearSms();
    }

    /** Clears the rate-limit counters so one test's failed attempts do not throttle the next. */
    private void flushRedis() {
        try (var socket = new java.net.Socket(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            var out = socket.getOutputStream();
            out.write("FLUSHALL\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("could not flush Redis", e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Verification-code capture
    // ---------------------------------------------------------------------------------------------

    /**
     * Reads the most recent verification code sent to an address.
     *
     * <p>The code exists only as a hash in the database and is never logged, so the delivered message
     * is the one legitimate source. The body is scanned for the six-digit code the template embeds.
     */
    protected String latestEmailOtp(String email) {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> findEmailOtp(email), otp -> otp != null);
    }

    /** Reads the newest SMS-delivered verification code (mobile channel). */
    protected String latestSmsOtp() {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(CapturingSmsConfiguration::latestSmsCode, code -> code != null);
    }

    private String findEmailOtp(String email) {
        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        // Newest first: a resend supersedes the earlier code.
        for (int i = received.length - 1; i >= 0; i--) {
            MimeMessage message = received[i];
            try {
                if (!String.join(",", toAddresses(message)).toLowerCase(Locale.ROOT)
                        .contains(email.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Matcher matcher = Pattern.compile("\\b([0-9]{6})\\b").matcher(extractBody(message));
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception e) {
                throw new IllegalStateException("could not read a delivered message", e);
            }
        }
        return null;
    }

    private static List<String> toAddresses(MimeMessage message) throws Exception {
        List<String> addresses = new ArrayList<>();
        var to = message.getRecipients(MimeMessage.RecipientType.TO);
        if (to != null) {
            for (var address : to) {
                addresses.add(address.toString());
            }
        }
        return addresses;
    }

    private static String extractBody(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            return flatten(multipart);
        }
        return String.valueOf(content);
    }

    private static String flatten(Multipart multipart) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            if (part.getContent() instanceof String text) {
                builder.append(text);
            } else if (part.getContent() instanceof Multipart nested) {
                builder.append(flatten(nested));
            }
        }
        return builder.toString();
    }

    protected void clearMail() {
        try {
            GREEN_MAIL.purgeEmailFromAllMailboxes();
        } catch (Exception e) {
            throw new IllegalStateException("could not clear the mail sink", e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Journey helpers
    // ---------------------------------------------------------------------------------------------

    /** A customer registered and fully verified, ready to sign in. */
    protected record VerifiedCustomer(String id, String email, String mobile, String password) {
    }

    /**
     * Registers a customer and completes both OTP challenges, leaving an account that can sign in.
     *
     * <p>This walks the real endpoints and reads the real delivered codes, so every test that needs a
     * signed-in customer also re-proves the registration and verification path. A short random suffix
     * keeps emails and mobiles unique within a test that registers several customers.
     */
    protected VerifiedCustomer registerVerifiedCustomer(String tag) {
        String email = tag + "-" + java.util.UUID.randomUUID() + "@choco.test";
        String mobile = randomMobile();
        String password = "Choco!Pass123";

        HttpResponse<String> registered = post("/api/v1/auth/register", jsonBody(Map.of(
                "fullName", "Test Customer",
                "email", email,
                "mobile", mobile,
                "password", password)));
        if (registered.statusCode() != 202) {
            throw new IllegalStateException("registration failed: " + registered.statusCode()
                    + " " + registered.body());
        }
        String customerId = json(registered).get("customerId").asText();

        String emailCode = latestEmailOtp(email);
        HttpResponse<String> emailVerified = post(
                "/api/v1/auth/customers/" + customerId + "/verifications?channel=EMAIL",
                jsonBody(Map.of("code", emailCode)));
        if (emailVerified.statusCode() != 204) {
            throw new IllegalStateException("email verification failed: " + emailVerified.statusCode()
                    + " " + emailVerified.body());
        }

        String mobileCode = latestSmsOtp();
        HttpResponse<String> mobileVerified = post(
                "/api/v1/auth/customers/" + customerId + "/verifications?channel=MOBILE",
                jsonBody(Map.of("code", mobileCode)));
        if (mobileVerified.statusCode() != 204) {
            throw new IllegalStateException("mobile verification failed: " + mobileVerified.statusCode()
                    + " " + mobileVerified.body());
        }

        return new VerifiedCustomer(customerId, email, mobile, password);
    }

    /** Signs a verified customer in and returns the access token. */
    protected String login(VerifiedCustomer customer) {
        HttpResponse<String> response = post("/api/v1/auth/login", jsonBody(Map.of(
                "identifier", customer.email(),
                "password", customer.password())));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("login failed: " + response.statusCode() + " " + response.body());
        }
        return json(response).get("tokens").get("accessToken").asText();
    }

    /** Signs in and returns the whole login response, for tests that need the refresh token too. */
    protected JsonNode loginResponse(VerifiedCustomer customer) {
        HttpResponse<String> response = post("/api/v1/auth/login", jsonBody(Map.of(
                "identifier", customer.email(),
                "password", customer.password())));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("login failed: " + response.statusCode() + " " + response.body());
        }
        return json(response);
    }

    private static String randomMobile() {
        // Indian mobile numbers start with 6-9; a random nine-digit tail keeps them unique.
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        return "9" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    /**
     * Reads the claims of a JWT without verifying it.
     *
     * <p>Used only to assert what the token <em>claims</em>; the server verifies the signature on
     * every protected request, which the HTTP tests exercise. Decoding here makes a role or
     * verification-flag assertion explicit rather than inferred from a successful call.
     */
    protected JsonNode claimsOf(String accessToken) {
        String[] parts = accessToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("not a JWT");
        }
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
        try {
            return objectMapper.readTree(decoded);
        } catch (Exception e) {
            throw new IllegalStateException("could not decode the token claims", e);
        }
    }
}
