package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.PasswordPolicy;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.application.fake.InMemoryPasswordResetTokenRepository;
import com.choco.identity.application.fake.InMemoryRefreshTokenRepository;
import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.OpaqueTokenHasher;
import com.choco.identity.application.port.PasswordResetTokenRepository;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.application.fake.TestAbuseGuard;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the reset flow end to end through the use case.
 *
 * <p>The important case is {@link Reset#findsTheTokenThatWasStored}. It is a regression test: the
 * service once hashed the token with the password hasher, whose salt makes every digest different,
 * so the stored row could never be found and every reset failed with RESET_TOKEN_INVALID. A test that
 * only asserted "a token is stored" would have passed against the broken code.
 */
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private InMemoryCustomerRepository customers;
    private InMemoryPasswordResetTokenRepository tokens;
    private List<String> sentResetTokens;
    private Fakes.FixedClock clock;
    private PasswordResetService service;
    private UUID customerId;

    /** A deterministic hasher: lookup requires the same input to yield the same digest. */
    private static final OpaqueTokenHasher DETERMINISTIC_HASHER = token -> "sha256:" + token;

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        tokens = new InMemoryPasswordResetTokenRepository();
        sentResetTokens = new ArrayList<>();
        clock = new Fakes.FixedClock(NOW);

        PasswordHasher passwordHasher = new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return "argon2:" + UUID.randomUUID() + ":" + rawPassword;
            }

            @Override
            public boolean matches(String rawPassword, String storedHash) {
                return storedHash.endsWith(":" + rawPassword);
            }

            @Override
            public boolean needsRehash(String storedHash) {
                return false;
            }
        };
        PasswordPolicy policy = raw -> raw != null && raw.length() >= 12 ? List.of() : List.of("too short");
        AccountNotificationPort notifications = new AccountNotificationPort() {
            @Override
            public void welcome(String email, String fullName) {
            }

            @Override
            public void passwordChanged(String email, String fullName) {
            }

            @Override
            public void passwordResetRequested(String email, String fullName, String resetToken) {
                sentResetTokens.add(resetToken);
            }
        };
        AuditLogPort auditLog = (actorType, actorId, action, targetType, targetId, outcome, detail, meta) -> {
        };

        Customer customer = Customer.register(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ContactDetails.of("Asha Rao", "asha@example.com", "9876543210"),
                passwordHasher.hash("OldPassword123"), NOW);
        customerId = customer.id();
        customers.save(customer);

        AuthenticationService authentication = new AuthenticationService(customers,
                new InMemoryRefreshTokenRepository(), passwordHasher,
                new NoopTokenService(), new Fakes.InMemoryTokenRevocationRegistry(), auditLog,
                TestAbuseGuard.create(), new Fakes.DirectTransactionRunner(), new Fakes.SequentialIdGenerator(), clock);
        IdGenerator ids = new Fakes.SequentialIdGenerator();

        service = new PasswordResetService(customers, tokens, DETERMINISTIC_HASHER, passwordHasher,
                policy, notifications, auditLog, authentication, ids,
                TestAbuseGuard.create(), new Fakes.DirectTransactionRunner(), clock);
    }

    @Nested
    @DisplayName("requesting a reset")
    class Request {

        @Test
        @DisplayName("emails a token and stores only its hash")
        void storesHashNotToken() {
            service.requestReset("asha@example.com", "203.0.113.7");

            assertThat(sentResetTokens).hasSize(1);
            String rawToken = sentResetTokens.getFirst();

            // The raw token must never be persisted; only its digest.
            assertThat(tokens.findByHash(rawToken)).isEmpty();
            assertThat(tokens.findByHash(DETERMINISTIC_HASHER.hash(rawToken))).isPresent();
        }

        @Test
        @DisplayName("does not reveal whether the address is registered")
        void doesNotRevealUnknownAddress() {
            service.requestReset("nobody@example.com", "203.0.113.7");

            assertThat(sentResetTokens).isEmpty();
        }

        @Test
        @DisplayName("ignores a malformed address without throwing")
        void ignoresMalformedAddress() {
            service.requestReset("not-an-email", "203.0.113.7");

            assertThat(sentResetTokens).isEmpty();
        }
    }

    @Nested
    @DisplayName("completing a reset")
    class Reset {

        /**
         * Regression: this failed for every caller while the service used the salted password hasher
         * for the lookup, because the digest computed at reset time never equalled the one stored at
         * request time.
         */
        @Test
        @DisplayName("finds the token that was stored and changes the password")
        void findsTheTokenThatWasStored() {
            service.requestReset("asha@example.com", "203.0.113.7");
            String rawToken = sentResetTokens.getFirst();

            service.resetPassword(rawToken, "NewPassword123");

            Customer updated = customers.findById(customerId).orElseThrow();
            assertThat(updated.passwordHash()).endsWith(":NewPassword123");
        }

        @Test
        @DisplayName("rejects an unknown token")
        void rejectsUnknownToken() {
            assertThatThrownBy(() -> service.resetPassword("no-such-token", "NewPassword123"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).errorCode())
                    .isEqualTo(ErrorCode.RESET_TOKEN_INVALID);
        }

        @Test
        @DisplayName("rejects a token that has already been used")
        void rejectsReusedToken() {
            service.requestReset("asha@example.com", "203.0.113.7");
            String rawToken = sentResetTokens.getFirst();
            service.resetPassword(rawToken, "NewPassword123");

            assertThatThrownBy(() -> service.resetPassword(rawToken, "AnotherPassword123"))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("rejects an expired token")
        void rejectsExpiredToken() {
            service.requestReset("asha@example.com", "203.0.113.7");
            String rawToken = sentResetTokens.getFirst();
            clock.advanceSeconds(31 * 60);

            assertThatThrownBy(() -> service.resetPassword(rawToken, "NewPassword123"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).errorCode())
                    .isEqualTo(ErrorCode.RESET_TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("rejects a weak new password and leaves the old one in place")
        void rejectsWeakPassword() {
            service.requestReset("asha@example.com", "203.0.113.7");
            String rawToken = sentResetTokens.getFirst();

            assertThatThrownBy(() -> service.resetPassword(rawToken, "short"))
                    .isInstanceOf(RuntimeException.class);

            assertThat(customers.findById(customerId).orElseThrow().passwordHash())
                    .endsWith(":OldPassword123");
        }

        @Test
        @DisplayName("issues a fresh token on a second request and invalidates the first")
        void secondRequestInvalidatesFirst() {
            service.requestReset("asha@example.com", "203.0.113.7");
            String first = sentResetTokens.getFirst();
            service.requestReset("asha@example.com", "203.0.113.7");
            String second = sentResetTokens.getLast();

            assertThat(second).isNotEqualTo(first);

            service.resetPassword(second, "NewPassword123");

            assertThatThrownBy(() -> service.resetPassword(first, "AnotherPassword123"))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    /** Minimal TokenService; password reset only needs session revocation from it. */
    private static final class NoopTokenService implements com.choco.identity.application.port.TokenService {
        @Override
        public com.choco.identity.application.port.IssuedToken issueAccessToken(
                com.choco.security.principal.AuthenticatedUser user, String sessionId, Instant now) {
            return new com.choco.identity.application.port.IssuedToken("access", now.plusSeconds(900));
        }

        @Override
        public String generateRefreshTokenValue() {
            return "refresh-" + UUID.randomUUID();
        }

        @Override
        public String hashRefreshToken(String tokenValue) {
            return "sha256:" + tokenValue;
        }

        @Override
        public java.time.Duration accessTokenTtl() {
            return java.time.Duration.ofMinutes(15);
        }
    }
}
