package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.UnauthenticatedException;
import com.choco.identity.application.command.LoginCommand;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.application.fake.InMemoryRefreshTokenRepository;
import com.choco.identity.application.port.IssuedToken;
import com.choco.identity.application.port.TokenPair;
import com.choco.identity.application.port.TokenService;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.core.port.PasswordHasher;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final String PASSWORD = "correct horse battery";
    private static final String HASH = "hashed:" + PASSWORD;

    private InMemoryCustomerRepository customers;
    private InMemoryRefreshTokenRepository refreshTokens;
    private Fakes.FixedClock clock;
    private AuthenticationService service;
    private Customer customer;

    /** Hasher that can be told to report an outdated hash, to exercise the rehash-on-login path. */
    private static final class TestHasher implements PasswordHasher {
        private boolean outdated;

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
            return outdated;
        }
    }

    private TestHasher hasher;

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        clock = Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z");
        hasher = new TestHasher();

        customer = Customer.register(UUID.randomUUID(),
                ContactDetails.of("Asha Menon", "asha@example.com", "+919876543210"), HASH, NOW);
        customer.verify(VerificationChannel.EMAIL, NOW);
        customer.verify(VerificationChannel.MOBILE, NOW);
        customer.pullDomainEvents();
        customers.save(customer);

        var tokenService = new TokenService() {
            @Override
            public IssuedToken issueAccessToken(AuthenticatedUser user, Instant now) {
                return new IssuedToken("access-" + user.userId(), now.plusSeconds(900));
            }

            @Override
            public String generateRefreshTokenValue() {
                return "refresh-" + UUID.randomUUID();
            }

            @Override
            public String hashRefreshToken(String tokenValue) {
                return "h:" + tokenValue;
            }
        };
        var audit = new com.choco.identity.application.port.AuditLogPort() {
            @Override
            public void record(String actorType, String actorId, String action, String targetType,
                               String targetId, String outcome, String detail, String correlationId) {
            }
        };

        service = new AuthenticationService(customers, refreshTokens, hasher, tokenService, audit,
                new Fakes.SequentialIdGenerator(), clock);
    }

    private LoginCommand login(String identifier, String password) {
        return new LoginCommand(identifier, password, "127.0.0.1", "JUnit");
    }

    @Test
    void a_verified_customer_can_sign_in_with_their_email() {
        var result = service.login(login("asha@example.com", PASSWORD));

        assertThat(result.customer().fullyVerified()).isTrue();
        assertThat(result.tokens().accessToken()).isNotBlank();
        assertThat(result.tokens().refreshToken()).isNotBlank();
        assertThat(result.tokens().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void a_customer_can_sign_in_with_their_mobile_number() {
        var result = service.login(login("+919876543210", PASSWORD));
        assertThat(result.customer().email()).isEqualTo("asha@example.com");
    }

    @Test
    void an_unknown_account_and_a_wrong_password_are_indistinguishable() {
        ErrorCode unknownAccount = errorCodeOf(() -> service.login(login("nobody@example.com", PASSWORD)));
        ErrorCode wrongPassword = errorCodeOf(() -> service.login(login("asha@example.com", "wrong password here")));

        assertThat(unknownAccount)
                .as("the same error for both prevents account enumeration")
                .isEqualTo(wrongPassword)
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void an_unverified_customer_cannot_sign_in_even_with_the_right_password() {
        Customer unverified = Customer.register(UUID.randomUUID(),
                ContactDetails.of("Bob Rao", "bob@example.com", "+919876543211"), HASH, NOW);
        customers.save(unverified);

        assertThatThrownBy(() -> service.login(login("bob@example.com", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    void an_account_locks_after_repeated_wrong_passwords() {
        for (int i = 0; i < Customer.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.login(login("asha@example.com", "wrong password here")))
                    .isInstanceOf(UnauthenticatedException.class);
        }

        assertThatThrownBy(() -> service.login(login("asha@example.com", PASSWORD)))
                .as("even the correct password must be refused while locked")
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void a_locked_account_recovers_after_the_lockout_window() {
        for (int i = 0; i < Customer.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.login(login("asha@example.com", "wrong password here")))
                    .isInstanceOf(UnauthenticatedException.class);
        }

        clock.advanceSeconds(16 * 60);

        var result = service.login(login("asha@example.com", PASSWORD));
        assertThat(result.tokens().accessToken()).isNotBlank();
    }

    @Test
    void a_successful_login_resets_the_failure_counter() {
        assertThatThrownBy(() -> service.login(login("asha@example.com", "wrong password here")));

        service.login(login("asha@example.com", PASSWORD));

        assertThat(customers.findById(customer.id()).orElseThrow().failedLoginAttempts()).isZero();
    }

    @Test
    void an_outdated_hash_is_upgraded_transparently_on_login() {
        hasher.outdated = true;

        service.login(login("asha@example.com", PASSWORD));

        assertThat(customers.findById(customer.id()).orElseThrow().passwordHash())
                .as("the hash must be re-computed with the current algorithm")
                .isEqualTo(HASH);
    }

    @Test
    void refresh_rotates_the_token_so_the_old_one_cannot_be_reused() {
        TokenPair first = service.login(login("asha@example.com", PASSWORD)).tokens();

        TokenPair second = service.refresh(first.refreshToken()).tokens();
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .as("the rotated token must be dead")
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void replaying_a_rotated_token_revokes_the_whole_family() {
        TokenPair first = service.login(login("asha@example.com", PASSWORD)).tokens();
        TokenPair second = service.refresh(first.refreshToken()).tokens();

        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .isInstanceOf(UnauthenticatedException.class);

        assertThatThrownBy(() -> service.refresh(second.refreshToken()))
                .as("detecting a replay must invalidate the attacker's stolen token too")
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void an_unknown_refresh_token_is_rejected() {
        assertThatThrownBy(() -> service.refresh("never-issued"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void logout_revokes_the_refresh_token_and_is_idempotent() {
        TokenPair tokens = service.login(login("asha@example.com", PASSWORD)).tokens();

        service.logout(tokens.refreshToken());
        assertThatThrownBy(() -> service.refresh(tokens.refreshToken()))
                .isInstanceOf(UnauthenticatedException.class);

        // A second logout with the same value must not fail.
        service.logout(tokens.refreshToken());
        service.logout(null);
    }

    @Test
    void revoking_all_sessions_kills_every_refresh_token() {
        TokenPair one = service.login(login("asha@example.com", PASSWORD)).tokens();
        TokenPair two = service.login(login("asha@example.com", PASSWORD)).tokens();

        int revoked = service.revokeAllSessions(customer.id());

        assertThat(revoked).isEqualTo(2);
        assertThatThrownBy(() -> service.refresh(one.refreshToken())).isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> service.refresh(two.refreshToken())).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void a_blank_identifier_is_treated_as_invalid_credentials() {
        assertThatThrownBy(() -> service.login(login("   ", PASSWORD)))
                .isInstanceOf(UnauthenticatedException.class);
    }

    private static ErrorCode errorCodeOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected an exception");
        } catch (com.choco.core.error.DomainException e) {
            return e.errorCode();
        }
    }
}
