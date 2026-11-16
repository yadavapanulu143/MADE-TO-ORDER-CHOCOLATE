package com.choco.identity.application.service;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.PasswordPolicy;
import com.choco.core.port.TransactionRunner;
import com.choco.identity.application.command.ChangePasswordCommand;
import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.port.CustomerRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Changes a signed-in customer's password after proving the current one.
 *
 * <p>Two controls make this safe. The current password must be verified before anything changes, so a
 * hijacked session cannot silently take over the account by setting a new password. And every refresh
 * token for the customer is revoked once the new password is stored, so any session an attacker
 * already holds dies with the change.
 *
 * <p>The new password is checked against the same policy as registration and reset, and is stored
 * only as a hash. Neither the raw password nor its hash is ever logged, returned or audited.
 */
public class PasswordChangeService {

    private final CustomerRepository customerRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AccountNotificationPort notifications;
    private final AuditLogPort auditLog;
    private final AuthenticationService authenticationService;
    private final AbuseGuard abuseGuard;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public PasswordChangeService(CustomerRepository customerRepository,
                                 PasswordHasher passwordHasher,
                                 PasswordPolicy passwordPolicy,
                                 AccountNotificationPort notifications,
                                 AuditLogPort auditLog,
                                 AuthenticationService authenticationService,
                                 AbuseGuard abuseGuard,
                                 TransactionRunner transactionRunner,
                                 Clock clock) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.authenticationService = authenticationService;
        this.abuseGuard = abuseGuard;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public void changePassword(UUID customerId, ChangePasswordCommand command) {
        Instant now = clock.now();

        // Checked before the hash comparison, so a flood of guesses costs one Redis round trip rather
        // than an Argon2id verification each. Keyed per customer because the caller is authenticated.
        abuseGuard.checkPasswordChange(customerId.toString());

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));

        if (!passwordHasher.matches(command.currentPassword(), customer.passwordHash())) {
            auditLog.record("CUSTOMER", customerId.toString(), "PASSWORD_CHANGE_FAILED", "CUSTOMER",
                    customerId.toString(), "FAILURE", "BAD_CURRENT_PASSWORD", null);
            // Reported as an unauthenticated-style business error rather than a validation error, so
            // the response does not confirm which field was wrong.
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS,
                    "Your current password is incorrect");
        }

        if (passwordHasher.matches(command.newPassword(), customer.passwordHash())) {
            throw new ValidationException(ErrorCode.WEAK_PASSWORD,
                    "Your new password must be different from your current one");
        }

        List<String> violations = passwordPolicy.violations(command.newPassword());
        if (!violations.isEmpty()) {
            throw new ValidationException(ErrorCode.WEAK_PASSWORD, String.join("; ", violations));
        }

        customer.changePassword(passwordHasher.hash(command.newPassword()), now);

        // One unit: the new hash and the session revocation. Committing the password without revoking
        // would leave the very sessions the change is meant to end still valid.
        int revoked = transactionRunner.execute(() -> {
            customerRepository.save(customer);
            return authenticationService.revokeAllSessions(customer.id());
        });

        notifications.passwordChanged(customer.contact().email().value(), customer.contact().fullName());
        auditLog.record("CUSTOMER", customerId.toString(), "PASSWORD_CHANGED", "CUSTOMER",
                customerId.toString(), "SUCCESS", "sessionsRevoked=" + revoked, null);
    }
}
