package com.choco.identity.application.service;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.UnauthenticatedException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.identity.application.command.LoginCommand;
import com.choco.identity.application.dto.AuthenticationResult;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.IssuedToken;
import com.choco.identity.application.port.RefreshTokenRepository;
import com.choco.identity.application.port.TokenPair;
import com.choco.identity.application.port.TokenService;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.domain.port.CustomerRepository;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.TokenRevocationRegistry;
import com.choco.core.port.TransactionRunner;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.Roles;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates a customer and issues tokens.
 *
 * <p>Two rules protect against account discovery and brute force.
 *
 * <p>First, a wrong password and an unknown account return the same error, so the endpoint cannot be
 * used to test whether an email is registered.
 *
 * <p>Second, when the account is locked or unverified, the password is still verified before the
 * specific reason is returned. If the reason were returned first, an attacker could distinguish
 * accounts without knowing the password. Verification is therefore performed unconditionally, and
 * only then is the account state checked.
 */
public class AuthenticationService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final CustomerRepository customerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final TokenRevocationRegistry revocationRegistry;
    private final AuditLogPort auditLog;
    private final AbuseGuard abuseGuard;
    private final TransactionRunner transactionRunner;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AuthenticationService(CustomerRepository customerRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 PasswordHasher passwordHasher,
                                 TokenService tokenService,
                                 TokenRevocationRegistry revocationRegistry,
                                 AuditLogPort auditLog,
                                 AbuseGuard abuseGuard,
                                 TransactionRunner transactionRunner,
                                 IdGenerator idGenerator,
                                 Clock clock) {
        this.customerRepository = customerRepository;
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

    public AuthenticationResult login(LoginCommand command) {
        Instant now = clock.now();

        // Checked before the password, and before the account lookup, so a flood costs one Redis
        // round trip rather than an Argon2id hash per request. Consumed for every attempt whether or
        // not the account exists, so the 429 is not an account-enumeration signal.
        abuseGuard.checkLogin(command.identifier(), command.ipAddress());

        Optional<Customer> found = findByEmailOrMobile(command.identifier());

        if (found.isEmpty()) {
            auditLog.record("ANONYMOUS", null, "LOGIN_FAILED", "CUSTOMER", null, "FAILURE",
                    "UNKNOWN_ACCOUNT", null);
            throw invalidCredentials();
        }

        Customer customer = found.get();
        boolean passwordMatches = passwordHasher.matches(command.password(), customer.passwordHash());

        if (!passwordMatches) {
            customer.recordFailedLogin(now);
            // Its own transaction: the attempt count is the lockout control, and it has to survive
            // the exception thrown immediately below.
            transactionRunner.executeInNewTransaction(() -> customerRepository.save(customer));
            auditLog.record("CUSTOMER", customer.id().toString(), "LOGIN_FAILED", "CUSTOMER",
                    customer.id().toString(), "FAILURE", "BAD_PASSWORD", null);
            throw invalidCredentials();
        }

        // The password is correct. Only now is it safe to report a more specific reason.
        if (!customer.fullyVerified()) {
            throw new com.choco.core.error.BusinessRuleException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Please verify your email and mobile number before signing in");
        }
        customer.assertUsable(now);

        if (passwordHasher.needsRehash(customer.passwordHash())) {
            // Transparent upgrade to the current algorithm, so the fleet migrates without forcing resets.
            customer.changePassword(passwordHasher.hash(command.password()), now);
        }
        customer.recordSuccessfulLogin(now);
        customerRepository.save(customer);

        // A correct password clears the account counter so a legitimate user who mistyped a few times
        // starts clean. The per-address counter is deliberately left alone.
        abuseGuard.loginSucceeded(command.identifier());

        TokenPair tokens = issueTokens(customer, now);
        auditLog.record("CUSTOMER", customer.id().toString(), "LOGIN_SUCCESS", "CUSTOMER",
                customer.id().toString(), "SUCCESS", null, null);

        return new AuthenticationResult(CustomerViews.from(customer), tokens);
    }

    /**
     * Exchanges a refresh token for a new pair, rotating the old one.
     *
     * <p>Rotation is the point: a refresh token is single-use. If a stolen token is replayed after
     * the legitimate client has already rotated it, the lookup fails and the attempt is visible in
     * the audit log.
     */
    public AuthenticationResult refresh(String refreshTokenValue) {
        Instant now = clock.now();
        String hash = tokenService.hashRefreshToken(refreshTokenValue);

        RefreshTokenRepository.StoredRefreshToken stored = refreshTokenRepository.findByHash(hash)
                .orElseThrow(() -> {
                    auditLog.record("ANONYMOUS", null, "REFRESH_FAILED", "REFRESH_TOKEN", null,
                            "FAILURE", "UNKNOWN_TOKEN", null);
                    return new UnauthenticatedException(ErrorCode.UNAUTHENTICATED,
                            "Your session has expired. Please sign in again.");
                });

        if (!stored.isActive(now)) {
            // A revoked token being presented again suggests replay; revoke the whole family. The
            // access tokens of every session in the family are retired too, since each one named its
            // session id as its jti and the family revocation is what makes the replay harmless.
            refreshTokenRepository.revokeAllForCustomer(stored.customerId(), now);
            revocationRegistry.revokeSubjectIssuedBefore(stored.customerId(), now,
                    now.plus(tokenService.accessTokenTtl()));
            auditLog.record("CUSTOMER", stored.customerId().toString(), "REFRESH_REPLAY_DETECTED",
                    "REFRESH_TOKEN", stored.id().toString(), "FAILURE", "REVOKED_TOKEN_REUSED", null);
            throw new UnauthenticatedException(ErrorCode.UNAUTHENTICATED,
                    "Your session has expired. Please sign in again.");
        }

        Customer customer = customerRepository.findById(stored.customerId())
                .orElseThrow(() -> new UnauthenticatedException(ErrorCode.UNAUTHENTICATED,
                        "Your session has expired. Please sign in again."));
        customer.assertUsable(now);

        // Rotation retires the session that presented the old refresh token: its access token is as
        // stale as the refresh token it came with, so a client holding the old pair cannot keep using
        // the old access token after it has been handed a new pair.
        refreshTokenRepository.revoke(stored.id(), now);
        revokeSession(stored.id().toString(), now);
        TokenPair tokens = issueTokens(customer, now);

        return new AuthenticationResult(CustomerViews.from(customer), tokens);
    }

    /** Revokes a single refresh token. Logout is idempotent: an unknown token is not an error. */
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        Instant now = clock.now();
        refreshTokenRepository.findByHash(tokenService.hashRefreshToken(refreshTokenValue))
                .ifPresent(stored -> {
                    refreshTokenRepository.revoke(stored.id(), now);
                    // The point of logout is that the credential stops working now. Revoking only the
                    // refresh token would leave the access token valid for the rest of its lifetime,
                    // which is exactly the gap this closes.
                    revokeSession(stored.id().toString(), now);
                    auditLog.record("CUSTOMER", stored.customerId().toString(), "LOGOUT", "REFRESH_TOKEN",
                            stored.id().toString(), "SUCCESS", null, null);
                });
    }

    /**
     * Revokes every session for a customer, used after a password change or reset.
     *
     * <p>Retires both the refresh tokens and any access token issued before the change, so a session an
     * attacker already holds dies with the new password rather than surviving until its access token
     * expires. A token minted by a sign-in <em>after</em> the change is issued later than the cutoff and
     * is unaffected, so the account can sign in again immediately.
     */
    public int revokeAllSessions(UUID customerId) {
        Instant now = clock.now();
        int revoked = refreshTokenRepository.revokeAllForCustomer(customerId, now);
        revocationRegistry.revokeSubjectIssuedBefore(customerId, now, now.plus(tokenService.accessTokenTtl()));
        return revoked;
    }

    private TokenPair issueTokens(Customer customer, Instant now) {
        AuthenticatedUser user = new AuthenticatedUser(
                customer.id(),
                customer.contact().email().value(),
                Roles.CUSTOMER,
                customer.isVerified(VerificationChannel.EMAIL),
                customer.isVerified(VerificationChannel.MOBILE),
                java.util.Set.of());

        // The session id is the refresh-token row id, decided before the token is minted so the access
        // token can name it as its jti. That is what ties the pair together: revoking the session later
        // (on logout, rotation or replay) invalidates the access token too.
        UUID sessionId = idGenerator.nextId();
        IssuedToken accessToken = tokenService.issueAccessToken(user, sessionId.toString(), now);
        String refreshValue = tokenService.generateRefreshTokenValue();
        Instant refreshExpiry = now.plus(REFRESH_TOKEN_TTL);
        refreshTokenRepository.store(sessionId, customer.id(),
                tokenService.hashRefreshToken(refreshValue), refreshExpiry, now);

        return TokenPair.bearer(accessToken.value(), refreshValue, accessToken.expiresAt(), refreshExpiry);
    }

    /**
     * Retires one session's access token the moment its refresh token is revoked.
     *
     * <p>The retention window is the access-token lifetime: after it elapses no token of this session
     * could still be presented, so the record expires on its own. A store failure is allowed to
     * propagate rather than swallowed: a logout that reported success while the access token still
     * worked would be worse than a visible error, and the caller can retry.
     */
    private void revokeSession(String sessionId, Instant now) {
        revocationRegistry.revokeSession(sessionId, now.plus(tokenService.accessTokenTtl()));
    }

    private Optional<Customer> findByEmailOrMobile(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            return EmailAddress.tryParse(trimmed).flatMap(customerRepository::findByEmail);
        }
        return PhoneNumber.tryParse(trimmed).flatMap(customerRepository::findByMobile);
    }

    private UnauthenticatedException invalidCredentials() {
        return new UnauthenticatedException(ErrorCode.INVALID_CREDENTIALS,
                "Incorrect email/mobile or password");
    }
}
