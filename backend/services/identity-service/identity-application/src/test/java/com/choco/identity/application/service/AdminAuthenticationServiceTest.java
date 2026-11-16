package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.UnauthenticatedException;
import com.choco.core.port.PasswordHasher;
import com.choco.identity.application.command.AdminLoginCommand;
import com.choco.identity.application.fake.InMemoryAdminRefreshTokenRepository;
import com.choco.identity.application.fake.InMemoryAdminRepository;
import com.choco.identity.application.fake.TestAbuseGuard;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.IssuedToken;
import com.choco.identity.application.port.TokenService;
import com.choco.identity.domain.admin.AdminAccount;
import com.choco.identity.domain.admin.AdminRole;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.Roles;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests administrator sign-in.
 *
 * <p>The behaviours that matter are that a wrong password and an unknown account are
 * indistinguishable, that the token carries the administrator role and its permissions, and that a
 * refresh token rotates. Each is asserted directly.
 */
@DisplayName("AdminAuthenticationService")
class AdminAuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final String PASSWORD = "correct horse battery";
    private static final String HASH = "hashed:" + PASSWORD;

    private InMemoryAdminRepository admins;
    private InMemoryAdminRefreshTokenRepository refreshTokens;
    private Fakes.FixedClock clock;
    private AdminAuthenticationService service;
    private AdminAccount admin;
    private AuthenticatedUser lastIssuedFor;

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

    @BeforeEach
    void setUp() {
        admins = new InMemoryAdminRepository();
        refreshTokens = new InMemoryAdminRefreshTokenRepository();
        clock = Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z");

        admin = AdminAccount.provision(UUID.randomUUID(), "Asha Menon",
                EmailAddress.of("owner@choco.example"), HASH, AdminRole.SUPER_ADMIN, NOW);
        admins.save(admin);

        var tokenService = new TokenService() {
            @Override
            public IssuedToken issueAccessToken(AuthenticatedUser user, Instant now) {
                lastIssuedFor = user;
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
        AuditLogPort audit = (actorType, actorId, action, targetType, targetId, outcome, detail, correlationId) -> {
        };

        service = new AdminAuthenticationService(admins, refreshTokens, new TestHasher(), tokenService,
                audit, TestAbuseGuard.create(), new Fakes.DirectTransactionRunner(),
                new Fakes.SequentialIdGenerator(), clock);
    }

    private AdminLoginCommand login(String email, String password) {
        return new AdminLoginCommand(email, password, "127.0.0.1", "JUnit");
    }

    @Test
    void an_administrator_can_sign_in_with_the_right_password() {
        var result = service.login(login("owner@choco.example", PASSWORD));

        assertThat(result.admin().email()).isEqualTo("owner@choco.example");
        assertThat(result.admin().role()).isEqualTo("SUPER_ADMIN");
        assertThat(result.tokens().accessToken()).isNotBlank();
        assertThat(result.tokens().refreshToken()).isNotBlank();
        assertThat(result.tokens().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void the_access_token_carries_the_admin_role_and_its_permissions() {
        service.login(login("owner@choco.example", PASSWORD));

        assertThat(lastIssuedFor.role()).isEqualTo(Roles.SUPER_ADMIN);
        assertThat(lastIssuedFor.isAdmin()).isTrue();
        assertThat(lastIssuedFor.permissions()).contains(Roles.PERM_ADMIN_MANAGE, Roles.PERM_PAYMENT_REFUND);
    }

    @Test
    void a_plain_admin_does_not_get_the_super_admin_permissions() {
        AdminAccount plain = AdminAccount.provision(UUID.randomUUID(), "Bob Rao",
                EmailAddress.of("ops@choco.example"), HASH, AdminRole.ADMIN, NOW);
        admins.save(plain);

        var result = service.login(login("ops@choco.example", PASSWORD));

        assertThat(result.admin().role()).isEqualTo("ADMIN");
        assertThat(lastIssuedFor.permissions()).contains(Roles.PERM_CUSTOMER_EXPORT);
        assertThat(lastIssuedFor.permissions()).doesNotContain(Roles.PERM_ADMIN_MANAGE,
                Roles.PERM_PAYMENT_REFUND, Roles.PERM_SETTINGS_WRITE);
    }

    @Test
    void an_unknown_account_and_a_wrong_password_are_indistinguishable() {
        ErrorCode unknownAccount = errorCodeOf(() -> service.login(login("nobody@choco.example", PASSWORD)));
        ErrorCode wrongPassword = errorCodeOf(() -> service.login(login("owner@choco.example", "wrong")));

        assertThat(unknownAccount)
                .as("the same error for both prevents administrator enumeration")
                .isEqualTo(wrongPassword)
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void a_deactivated_administrator_cannot_sign_in_even_with_the_right_password() {
        admin.deactivate(NOW);
        admins.save(admin);

        assertThatThrownBy(() -> service.login(login("owner@choco.example", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void a_locked_administrator_cannot_sign_in_even_with_the_right_password() {
        for (int i = 0; i < AdminAccount.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            admin.recordFailedLogin(NOW);
        }
        admins.save(admin);

        assertThatThrownBy(() -> service.login(login("owner@choco.example", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void refresh_rotates_the_token_and_revokes_the_old_one() {
        var first = service.login(login("owner@choco.example", PASSWORD));
        String originalRefresh = first.tokens().refreshToken();

        var refreshed = service.refresh(originalRefresh);

        assertThat(refreshed.tokens().refreshToken()).isNotEqualTo(originalRefresh);
        assertThat(refreshTokens.findByHash("h:" + originalRefresh).orElseThrow().revokedAt()).isNotNull();
    }

    @Test
    void a_revoked_refresh_token_cannot_be_replayed_and_revokes_the_family() {
        var first = service.login(login("owner@choco.example", PASSWORD));
        String originalRefresh = first.tokens().refreshToken();
        var second = service.refresh(originalRefresh);

        // The old token is now revoked; presenting it again is a replay.
        assertThatThrownBy(() -> service.refresh(originalRefresh))
                .isInstanceOf(UnauthenticatedException.class)
                .extracting(e -> ((UnauthenticatedException) e).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        // Replay detection revokes the whole family, so the legitimate new token is dead too.
        assertThat(refreshTokens.findByHash("h:" + second.tokens().refreshToken()).orElseThrow()
                .revokedAt()).isNotNull();
    }

    @Test
    void an_unknown_refresh_token_is_rejected() {
        assertThatThrownBy(() -> service.refresh("not-a-real-token"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void logout_revokes_the_refresh_token_and_is_idempotent() {
        var first = service.login(login("owner@choco.example", PASSWORD));
        String refresh = first.tokens().refreshToken();

        service.logout(refresh);
        service.logout(refresh);

        assertThat(refreshTokens.findByHash("h:" + refresh).orElseThrow().revokedAt()).isNotNull();
    }

    @Test
    void revoking_all_sessions_kills_every_active_token() {
        service.login(login("owner@choco.example", PASSWORD));
        service.login(login("owner@choco.example", PASSWORD));

        int revoked = service.revokeAllSessions(admin.id());

        assertThat(revoked).isEqualTo(2);
    }

    @Test
    void a_customer_account_cannot_sign_in_at_the_admin_endpoint() {
        // "customer@choco.example" exists only as a customer. Administrators live in their own table
        // and the admin service never consults customers, so no self-registered account can obtain an
        // administrator token by presenting its own credentials here.
        com.choco.identity.application.fake.InMemoryCustomerRepository customers =
                new com.choco.identity.application.fake.InMemoryCustomerRepository();
        customers.save(com.choco.identity.domain.customer.Customer.register(UUID.randomUUID(),
                com.choco.identity.domain.customer.ContactDetails.of("Not An Admin",
                        "customer@choco.example", "+919876543210"),
                "hashed:a-customer-password", NOW));

        assertThatThrownBy(() -> service.login(login("customer@choco.example", "a-customer-password")))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void the_issued_role_is_always_an_administrator_role() {
        // The token's role comes from the administrator row, never from any request input, so it can
        // only ever be ADMIN or SUPER_ADMIN.
        service.login(login("owner@choco.example", PASSWORD));

        assertThat(lastIssuedFor.role()).isIn(Roles.ADMIN, Roles.SUPER_ADMIN);
        assertThat(lastIssuedFor.isCustomer()).isFalse();
    }

    private static ErrorCode errorCodeOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected an exception");
        } catch (UnauthenticatedException e) {
            return e.errorCode();
        }
    }
}
