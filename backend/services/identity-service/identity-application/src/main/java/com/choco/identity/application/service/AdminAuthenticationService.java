package com.choco.identity.application.service;

import com.choco.core.domain.EmailAddress;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.UnauthenticatedException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.TokenRevocationRegistry;
import com.choco.core.port.TransactionRunner;
import com.choco.identity.application.command.AdminLoginCommand;
import com.choco.identity.application.dto.AdminAuthenticationResult;
import com.choco.identity.application.dto.AdminView;
import com.choco.identity.application.port.AdminRefreshTokenRepository;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.IssuedToken;
import com.choco.identity.application.port.TokenPair;
import com.choco.identity.application.port.TokenService;
import com.choco.identity.domain.admin.AdminAccount;
import com.choco.identity.domain.admin.AdminRole;
import com.choco.identity.domain.port.AdminRepository;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.Roles;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticates an administrator and issues tokens.
 *
 * <p>Structurally the same as customer authentication, for the same reasons: a wrong password and an
 * unknown account return an identical error so the endpoint cannot enumerate administrators, the
 * password is verified before the account state is reported, and the attempt is rate-limited before
 * the hash is computed.
 *
 * <p>The access token it issues carries {@code role=ADMIN|SUPER_ADMIN} and the fine-grained
 * permissions for that role. Downstream services trust the signature, so this is the only place that
 * decides what an administrator may do.
 */
public class AdminAuthenticationService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final AdminRepository adminRepository;
    private final AdminRefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final TokenRevocationRegistry revocationRegistry;
    private final AuditLogPort auditLog;
    private final AbuseGuard abuseGuard;
    private final TransactionRunner transactionRunner;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AdminAuthenticationService(AdminRepository adminRepository,
                                      AdminRefreshTokenRepository refreshTokenRepository,
                                      PasswordHasher passwordHasher,
                                      TokenService tokenService,
                                      TokenRevocationRegistry revocationRegistry,
                                      AuditLogPort auditLog,
                                      AbuseGuard abuseGuard,
                                      TransactionRunner transactionRunner,
                                      IdGenerator idGenerator,
                                      Clock clock) {
        this.adminRepository = adminRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.revocationRegistry = revocationRegistry;
        this.auditLog = auditLog;
        this.abuseGuard = abuseGuard;
        this.transactionRunner = transactionRunner;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public AdminAuthenticationResult login(AdminLoginCommand command) {
        Instant now = clock.now();

        abuseGuard.checkAdminLogin(command.email(), command.ipAddress());

        Optional<AdminAccount> found = EmailAddress.tryParse(command.email())
                .flatMap(adminRepository::findByEmail);

        if (found.isEmpty()) {
            auditLog.record("ANONYMOUS", null, "ADMIN_LOGIN_FAILED", "ADMIN", null, "FAILURE",
                    "UNKNOWN_ACCOUNT", null);
            throw invalidCredentials();
        }

        AdminAccount admin = found.get();
        boolean passwordMatches = passwordHasher.matches(command.password(), admin.passwordHash());

        if (!passwordMatches) {
            admin.recordFailedLogin(now);
            // Its own transaction: the attempt count is the lockout control and must survive the
            // exception thrown immediately below.
            transactionRunner.executeInNewTransaction(() -> adminRepository.save(admin));
            auditLog.record("ADMIN", admin.id().toString(), "ADMIN_LOGIN_FAILED", "ADMIN",
                    admin.id().toString(), "FAILURE", "BAD_PASSWORD", null);
            throw invalidCredentials();
        }

        // The password is correct. Only now is it safe to report a more specific reason.
        admin.assertUsable(now);

        if (passwordHasher.needsRehash(admin.passwordHash())) {
            admin.changePassword(passwordHasher.hash(command.password()), now);
        }
        admin.recordSuccessfulLogin(now);
        adminRepository.save(admin);

        abuseGuard.adminLoginSucceeded(command.email());

        TokenPair tokens = issueTokens(admin, now);
        auditLog.record("ADMIN", admin.id().toString(), "ADMIN_LOGIN_SUCCESS", "ADMIN",
                admin.id().toString(), "SUCCESS", "role=" + admin.role(), null);

        return new AdminAuthenticationResult(toView(admin), tokens);
    }

    /**
     * Exchanges an administrator refresh token for a new pair, rotating the old one.
     *
     * <p>Replay of a revoked token revokes the whole family, exactly as for customers: a rotated
     * token being presented again means someone has a copy they should not.
     */
    public AdminAuthenticationResult refresh(String refreshTokenValue) {
        Instant now = clock.now();
        String hash = tokenService.hashRefreshToken(refreshTokenValue);

        AdminRefreshTokenRepository.StoredAdminRefreshToken stored = refreshTokenRepository.findByHash(hash)
                .orElseThrow(() -> {
                    auditLog.record("ANONYMOUS", null, "ADMIN_REFRESH_FAILED", "ADMIN_REFRESH_TOKEN", null,
                            "FAILURE", "UNKNOWN_TOKEN", null);
                    return new UnauthenticatedException(ErrorCode.UNAUTHENTICATED,
                            "Your session has expired. Please sign in again.");
                });

        if (!stored.isActive(now)) {
            refreshTokenRepository.revokeAllForAdmin(stored.adminId(), now);
            revocationRegistry.revokeSubjectIssuedBefore(stored.adminId(), now,
                    now.plus(tokenService.accessTokenTtl()));
            auditLog.record("ADMIN", stored.adminId().toString(), "ADMIN_REFRESH_REPLAY_DETECTED",
                    "ADMIN_REFRESH_TOKEN", stored.id().toString(), "FAILURE", "REVOKED_TOKEN_REUSED", null);
            throw new UnauthenticatedException(ErrorCode.UNAUTHENTICATED,
                    "Your session has expired. Please sign in again.");
        }

        AdminAccount admin = adminRepository.findById(stored.adminId())
                .orElseThrow(() -> new UnauthenticatedException(ErrorCode.UNAUTHENTICATED,
                        "Your session has expired. Please sign in again."));
        admin.assertUsable(now);

        refreshTokenRepository.revoke(stored.id(), now);
        revokeSession(stored.id().toString(), now);
        TokenPair tokens = issueTokens(admin, now);

        return new AdminAuthenticationResult(toView(admin), tokens);
    }

    /** Revokes a single administrator refresh token. Logout is idempotent. */
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        Instant now = clock.now();
        refreshTokenRepository.findByHash(tokenService.hashRefreshToken(refreshTokenValue))
                .ifPresent(stored -> {
                    refreshTokenRepository.revoke(stored.id(), now);
                    // Retire the access token too, or a logged-out administrator keeps a working
                    // credential for the rest of its lifetime.
                    revokeSession(stored.id().toString(), now);
                    auditLog.record("ADMIN", stored.adminId().toString(), "ADMIN_LOGOUT",
                            "ADMIN_REFRESH_TOKEN", stored.id().toString(), "SUCCESS", null, null);
                });
    }

    /** Revokes every session for an administrator, used after a password change. */
    public int revokeAllSessions(UUID adminId) {
        Instant now = clock.now();
        int revoked = refreshTokenRepository.revokeAllForAdmin(adminId, now);
        revocationRegistry.revokeSubjectIssuedBefore(adminId, now, now.plus(tokenService.accessTokenTtl()));
        return revoked;
    }

    static AdminView toView(AdminAccount admin) {
        return new AdminView(admin.id(), admin.fullName(), admin.email().value(), admin.role().name(),
                AdminPermissions.forRole(admin.role()), admin.isActive(), admin.createdAt());
    }

    private TokenPair issueTokens(AdminAccount admin, Instant now) {
        AuthenticatedUser user = new AuthenticatedUser(
                admin.id(),
                admin.email().value(),
                admin.role().name(),
                // An administrator has no OTP channels. Reporting them verified would be a lie that a
                // downstream "must be fully verified" check could misread, so both are false and the
                // admin role is what grants access.
                false,
                false,
                AdminPermissions.forRole(admin.role()));

        // The session id is the refresh-token row id and becomes the access token's jti, so revoking
        // the session on logout invalidates the access token as well as the refresh token.
        UUID sessionId = idGenerator.nextId();
        IssuedToken accessToken = tokenService.issueAccessToken(user, sessionId.toString(), now);
        String refreshValue = tokenService.generateRefreshTokenValue();
        Instant refreshExpiry = now.plus(REFRESH_TOKEN_TTL);
        refreshTokenRepository.store(sessionId, admin.id(),
                tokenService.hashRefreshToken(refreshValue), refreshExpiry, now);

        return TokenPair.bearer(accessToken.value(), refreshValue, accessToken.expiresAt(), refreshExpiry);
    }

    /** Retires a session's access token when its refresh token is revoked. */
    private void revokeSession(String sessionId, Instant now) {
        revocationRegistry.revokeSession(sessionId, now.plus(tokenService.accessTokenTtl()));
    }

    private UnauthenticatedException invalidCredentials() {
        return new UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS,
                "Incorrect email address or password");
    }
}
