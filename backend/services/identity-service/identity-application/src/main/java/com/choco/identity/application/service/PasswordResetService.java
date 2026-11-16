package com.choco.identity.application.service;

import com.choco.core.domain.EmailAddress;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.PasswordResetTokenRepository;
import com.choco.identity.domain.port.CustomerRepository;
import com.choco.identity.domain.port.PasswordHasher;
import com.choco.identity.domain.port.PasswordPolicyPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Password reset by emailed token.
 *
 * <p>Requesting a reset never reveals whether the address is registered: the response is identical
 * either way, and only the audit log records the difference. Otherwise the endpoint becomes an
 * account-enumeration oracle.
 *
 * <p>The token is stored as a hash and is single-use. Completing a reset revokes every refresh token
 * for the customer, so a stolen session cannot outlive the password change that was meant to end it.
 */
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final CustomerRepository customerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyPort passwordPolicy;
    private final AccountNotificationPort notifications;
    private final AuditLogPort auditLog;
    private final AuthenticationService authenticationService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PasswordResetService(CustomerRepository customerRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordHasher passwordHasher,
                                PasswordPolicyPort passwordPolicy,
                                AccountNotificationPort notifications,
                                AuditLogPort auditLog,
                                AuthenticationService authenticationService,
                                IdGenerator idGenerator,
                                Clock clock) {
        this.customerRepository = customerRepository;
        this.tokenRepository = tokenRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.authenticationService = authenticationService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** Always completes without revealing whether the address exists. */
    public void requestReset(String emailRaw) {
        Instant now = clock.now();
        var email = EmailAddress.tryParse(emailRaw);
        if (email.isEmpty()) {
            auditLog.record("ANONYMOUS", null, "PASSWORD_RESET_REQUESTED", "CUSTOMER", null,
                    "SUCCESS", "MALFORMED_IDENTIFIER", null);
            return;
        }

        var customer = customerRepository.findByEmail(email.get());
        if (customer.isEmpty()) {
            auditLog.record("ANONYMOUS", null, "PASSWORD_RESET_REQUESTED", "CUSTOMER", null,
                    "SUCCESS", "UNKNOWN_ACCOUNT", null);
            return;
        }

        var found = customer.get();
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        tokenRepository.store(idGenerator.nextId(), found.id(), passwordHasher.hash(rawToken),
                now.plus(TOKEN_TTL), now);

        notifications.passwordResetRequested(found.contact().email().value(),
                found.contact().fullName(), rawToken);
        auditLog.record("CUSTOMER", found.id().toString(), "PASSWORD_RESET_REQUESTED", "CUSTOMER",
                found.id().toString(), "SUCCESS", null, null);
    }

    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.now();

        PasswordResetTokenRepository.StoredResetToken stored =
                tokenRepository.findByHash(passwordHasher.hash(rawToken))
                        .orElseThrow(() -> new BusinessRuleException(ErrorCode.RESET_TOKEN_INVALID,
                                "This password reset link is not valid. Please request a new one."));

        if (!stored.isActive(now)) {
            throw new BusinessRuleException(ErrorCode.RESET_TOKEN_EXPIRED,
                    "This password reset link has expired. Please request a new one.");
        }

        List<String> violations = passwordPolicy.violations(newPassword);
        if (!violations.isEmpty()) {
            throw new ValidationException(ErrorCode.WEAK_PASSWORD, String.join("; ", violations));
        }

        var customer = customerRepository.findById(stored.customerId())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.RESET_TOKEN_INVALID,
                        "This password reset link is not valid. Please request a new one."));

        customer.changePassword(passwordHasher.hash(newPassword), now);
        customerRepository.save(customer);
        tokenRepository.consume(stored.id(), now);
        tokenRepository.consumeAllForCustomer(stored.customerId(), now);

        int revoked = authenticationService.revokeAllSessions(customer.id());
        notifications.passwordChanged(customer.contact().email().value(), customer.contact().fullName());
        auditLog.record("CUSTOMER", customer.id().toString(), "PASSWORD_RESET_COMPLETED", "CUSTOMER",
                customer.id().toString(), "SUCCESS", "sessionsRevoked=" + revoked, null);
    }
}
