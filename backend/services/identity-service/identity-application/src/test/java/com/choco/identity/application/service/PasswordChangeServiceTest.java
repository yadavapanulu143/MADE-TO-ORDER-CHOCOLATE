package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.RateLimitedException;
import com.choco.core.error.ValidationException;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.PasswordPolicy;
import com.choco.identity.application.command.ChangePasswordCommand;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.application.fake.InMemoryOtpChallengeRepository;
import com.choco.identity.application.fake.InMemoryRefreshTokenRepository;
import com.choco.identity.application.fake.SnapshottingTransactionRunner;
import com.choco.identity.application.fake.TestAbuseGuard;
import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
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
 * Tests self-service password change.
 *
 * <p>The two behaviours that matter are that a wrong current password changes nothing, and that a
 * successful change revokes every other session. Both are asserted directly, because a service that
 * skipped either would pass a happy-path-only test.
 */
@DisplayName("PasswordChangeService")
class PasswordChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final String CURRENT = "current horse battery";
    private static final String NEW = "brand new passphrase 42";

    /**
     * Minimal TokenService. Password change only needs session revocation, which the registry supplies;
     * nothing here is ever called on a successful change path that mints a token.
     */
    private static final com.choco.identity.application.port.TokenService NOOP_TOKEN_SERVICE =
            new com.choco.identity.application.port.TokenService() {
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
                    return "h:" + tokenValue;
                }

                @Override
                public java.time.Duration accessTokenTtl() {
                    return java.time.Duration.ofMinutes(15);
                }
            };

    private InMemoryCustomerRepository customers;
    private InMemoryRefreshTokenRepository refreshTokens;
    private List<String> notified;
    private List<String[]> auditRecords;
    private PasswordChangeService service;
    private final UUID asha = UUID.randomUUID();

    /** Reversible stand-in for the real hasher: enough to assert change/no-change precisely. */
    private static final class TestHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return storedHash.equals(hash(rawPassword));
        }

        @Override
        public boolean needsRehash(String storedHash) {
            return false;
        }
    }

    /** Policy that accepts anything long enough, so strength is not the subject of these tests. */
    private static final PasswordPolicy LENIENT = raw ->
            raw != null && raw.length() >= 8 ? List.of() : List.of("Password is too short");

    private static final AccountNotificationPort NO_NOTIFICATIONS = new AccountNotificationPort() {
        @Override
        public void welcome(String email, String fullName) {
        }

        @Override
        public void passwordChanged(String email, String fullName) {
        }

        @Override
        public void passwordResetRequested(String email, String fullName, String resetToken) {
        }
    };

    private static final AuditLogPort NO_AUDIT = (a, b, c, d, e, f, g, h) -> {
    };

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        notified = new ArrayList<>();
        auditRecords = new ArrayList<>();

        AccountNotificationPort notifications = new AccountNotificationPort() {
            @Override
            public void welcome(String email, String fullName) {
            }

            @Override
            public void passwordChanged(String email, String fullName) {
                notified.add(email);
            }

            @Override
            public void passwordResetRequested(String email, String fullName, String resetToken) {
            }
        };
        AuditLogPort auditLog = (actorType, actorId, action, targetType, targetId, outcome, detail, meta) ->
                auditRecords.add(new String[] {action, outcome, detail});

        AuthenticationService authenticationService = new AuthenticationService(customers, refreshTokens,
                new TestHasher(), NOOP_TOKEN_SERVICE, new Fakes.InMemoryTokenRevocationRegistry(), auditLog,
                TestAbuseGuard.create(),
                new SnapshottingTransactionRunner(customers, new InMemoryOtpChallengeRepository()),
                new Fakes.SequentialIdGenerator(), new Fakes.FixedClock(NOW));

        service = new PasswordChangeService(customers, new TestHasher(), LENIENT, notifications, auditLog,
                authenticationService, TestAbuseGuard.create(),
                new SnapshottingTransactionRunner(customers, new InMemoryOtpChallengeRepository()),
                new Fakes.FixedClock(NOW));
    }

    private Customer seed() {
        Customer customer = Customer.register(asha, ContactDetails.of("Asha Rao", "asha@example.com",
                "9876543210"), new TestHasher().hash(CURRENT), NOW);
        customer.verify(VerificationChannel.EMAIL, NOW);
        customer.verify(VerificationChannel.MOBILE, NOW);
        customers.save(customer);
        return customer;
    }

    @Nested
    @DisplayName("successful change")
    class Successful {

        @Test
        void replaces_the_stored_hash() {
            seed();

            service.changePassword(asha, new ChangePasswordCommand(CURRENT, NEW));

            Customer stored = customers.findById(asha).orElseThrow();
            assertThat(stored.passwordHash()).isEqualTo(new TestHasher().hash(NEW));
            assertThat(stored.passwordHash()).isNotEqualTo(new TestHasher().hash(CURRENT));
        }

        @Test
        void the_new_password_verifies_and_the_old_one_does_not() {
            seed();

            service.changePassword(asha, new ChangePasswordCommand(CURRENT, NEW));

            Customer stored = customers.findById(asha).orElseThrow();
            assertThat(new TestHasher().matches(NEW, stored.passwordHash())).isTrue();
            assertThat(new TestHasher().matches(CURRENT, stored.passwordHash())).isFalse();
        }

        @Test
        void notifies_the_customer() {
            seed();

            service.changePassword(asha, new ChangePasswordCommand(CURRENT, NEW));

            assertThat(notified).containsExactly("asha@example.com");
        }

        @Test
        void records_an_audit_entry_without_password_material() {
            seed();

            service.changePassword(asha, new ChangePasswordCommand(CURRENT, NEW));

            assertThat(auditRecords)
                    .anySatisfy(record -> assertThat(record[0]).isEqualTo("PASSWORD_CHANGED"));
            assertThat(auditRecords)
                    .allSatisfy(record -> assertThat(String.join("|", record))
                            .doesNotContain(CURRENT)
                            .doesNotContain(NEW)
                            .doesNotContain("hashed:"));
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @Test
        void a_wrong_current_password_changes_nothing() {
            seed();
            String before = customers.findById(asha).orElseThrow().passwordHash();

            assertThatThrownBy(() -> service.changePassword(asha,
                    new ChangePasswordCommand("wrong password here", NEW)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).errorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            assertThat(customers.findById(asha).orElseThrow().passwordHash())
                    .as("a rejected change must leave the old password in place")
                    .isEqualTo(before);
            assertThat(notified).isEmpty();
        }

        @Test
        void a_wrong_current_password_is_audited_as_a_failure() {
            seed();

            assertThatThrownBy(() -> service.changePassword(asha,
                    new ChangePasswordCommand("wrong password here", NEW)))
                    .isInstanceOf(BusinessRuleException.class);

            assertThat(auditRecords)
                    .anySatisfy(record -> {
                        assertThat(record[0]).isEqualTo("PASSWORD_CHANGE_FAILED");
                        assertThat(record[1]).isEqualTo("FAILURE");
                    });
        }

        @Test
        void rejects_a_new_password_that_breaks_the_policy() {
            seed();

            assertThatThrownBy(() -> service.changePassword(asha,
                    new ChangePasswordCommand(CURRENT, "short")))
                    .isInstanceOf(ValidationException.class)
                    .extracting(e -> ((ValidationException) e).errorCode())
                    .isEqualTo(ErrorCode.WEAK_PASSWORD);
        }

        @Test
        @DisplayName("reusing the current password is rejected")
        void rejectsReusingTheCurrentPassword() {
            seed();

            assertThatThrownBy(() -> service.changePassword(asha,
                    new ChangePasswordCommand(CURRENT, CURRENT)))
                    .isInstanceOf(ValidationException.class)
                    .extracting(e -> ((ValidationException) e).errorCode())
                    .isEqualTo(ErrorCode.WEAK_PASSWORD);
        }

        @Test
        void rejects_an_unknown_customer() {
            assertThatThrownBy(() -> service.changePassword(UUID.randomUUID(),
                    new ChangePasswordCommand(CURRENT, NEW)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("session revocation")
    class SessionRevocation {

        @Test
        void revokes_every_refresh_token_on_success() {
            seed();
            UUID tokenA = UUID.randomUUID();
            UUID tokenB = UUID.randomUUID();
            refreshTokens.store(tokenA, asha, "hash:a", NOW.plusSeconds(3600), NOW);
            refreshTokens.store(tokenB, asha, "hash:b", NOW.plusSeconds(3600), NOW);

            service.changePassword(asha, new ChangePasswordCommand(CURRENT, NEW));

            assertThat(refreshTokens.findByHash("hash:a").orElseThrow().isActive(NOW))
                    .as("an old session must not outlive the password change")
                    .isFalse();
            assertThat(refreshTokens.findByHash("hash:b").orElseThrow().isActive(NOW)).isFalse();
        }

        @Test
        void reports_the_revocation_count_in_the_audit_entry() {
            seed();
            refreshTokens.store(UUID.randomUUID(), asha, "hash:a", NOW.plusSeconds(3600), NOW);

            service.changePassword(asha, new ChangePasswordCommand(CURRENT, NEW));

            assertThat(auditRecords)
                    .anySatisfy(record -> {
                        assertThat(record[0]).isEqualTo("PASSWORD_CHANGED");
                        assertThat(record[2]).isEqualTo("sessionsRevoked=1");
                    });
        }
    }

    @Nested
    @DisplayName("rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("guessing the current password is rate limited")
        void guessingIsRateLimited() {
            seed();
            var strict = TestAbuseGuard.withPolicy(new AbuseGuard.Policy(
                    10, 20, java.time.Duration.ofMinutes(15),
                    10, java.time.Duration.ofMinutes(15),
                    5, java.time.Duration.ofMinutes(15),
                    3, 10, java.time.Duration.ofMinutes(15),
                    2, java.time.Duration.ofMinutes(15),
                    10, java.time.Duration.ofHours(1)));
            AuthenticationService auth = new AuthenticationService(customers, refreshTokens,
                    new TestHasher(), NOOP_TOKEN_SERVICE, new Fakes.InMemoryTokenRevocationRegistry(), NO_AUDIT, strict,
                    new SnapshottingTransactionRunner(customers, new InMemoryOtpChallengeRepository()),
                    new Fakes.SequentialIdGenerator(), new Fakes.FixedClock(NOW));
            PasswordChangeService limited = new PasswordChangeService(customers, new TestHasher(),
                    LENIENT, NO_NOTIFICATIONS, NO_AUDIT, auth, strict,
                    new SnapshottingTransactionRunner(customers, new InMemoryOtpChallengeRepository()),
                    new Fakes.FixedClock(NOW));

            // The first two guesses are rejected as a wrong password; the third is refused by the
            // limiter before the password is even compared.
            assertThatThrownBy(() -> limited.changePassword(asha, new ChangePasswordCommand("wrong one", NEW)))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> limited.changePassword(asha, new ChangePasswordCommand("wrong two", NEW)))
                    .isInstanceOf(BusinessRuleException.class);

            assertThatThrownBy(() -> limited.changePassword(asha,
                    new ChangePasswordCommand("wrong three", NEW)))
                    .as("a flood of current-password guesses must be throttled")
                    .isInstanceOf(RateLimitedException.class);
        }
    }
}
