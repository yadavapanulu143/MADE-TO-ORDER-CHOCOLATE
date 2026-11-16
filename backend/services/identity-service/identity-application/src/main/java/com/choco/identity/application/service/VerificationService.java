package com.choco.identity.application.service;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.identity.application.command.VerifyOtpCommand;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.domain.otp.OtpChallenge;
import com.choco.identity.domain.port.CustomerRepository;
import com.choco.identity.domain.port.OtpChallengeRepository;
import com.choco.identity.domain.port.OtpCodeService;
import java.time.Instant;
import java.util.UUID;

/**
 * Verifies an email or mobile one-time password.
 *
 * <p>Two details are deliberate. First, a wrong code and an unknown customer produce different
 * errors here because the caller is already authenticated as that customer; there is no account
 * enumeration risk. Second, the welcome message is sent only when the <em>last</em> channel becomes
 * verified, so it cannot fire twice.
 */
public class VerificationService {

    private final CustomerRepository customerRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final OtpCodeService otpCodeService;
    private final AccountNotificationPort notifications;
    private final AuditLogPort auditLog;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public VerificationService(CustomerRepository customerRepository,
                               OtpChallengeRepository otpChallengeRepository,
                               OtpCodeService otpCodeService,
                               AccountNotificationPort notifications,
                               AuditLogPort auditLog,
                               EventPublisher eventPublisher,
                               Clock clock) {
        this.customerRepository = customerRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.otpCodeService = otpCodeService;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public CustomerView verify(VerifyOtpCommand command) {
        UUID customerId = parseCustomerId(command.customerId());
        VerificationChannel channel = parseChannel(command.channel());
        Instant now = clock.now();

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));

        if (customer.isVerified(channel)) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "This " + channel.name().toLowerCase() + " is already verified");
        }

        OtpChallenge challenge = otpChallengeRepository.findLatestActive(customerId, channel.name())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.OTP_INVALID,
                        "No active verification code. Please request a new one."));

        boolean matched = otpCodeService.matches(command.code(), challenge.codeHash());
        try {
            challenge.attempt(matched, now);
        } catch (BusinessRuleException failure) {
            otpChallengeRepository.save(challenge);
            auditLog.record("CUSTOMER", customerId.toString(), "OTP_VERIFICATION_FAILED", "OTP",
                    challenge.id().toString(), "FAILURE", failure.errorCode().name(), null);
            throw failure;
        }
        otpChallengeRepository.save(challenge);

        boolean wasFullyVerified = customer.fullyVerified();
        customer.verify(channel, now);
        customerRepository.save(customer);

        eventPublisher.publishAll(customer.pullDomainEvents());

        if (!wasFullyVerified && customer.fullyVerified()) {
            notifications.welcome(customer.contact().email().value(), customer.contact().fullName());
        }
        auditLog.record("CUSTOMER", customerId.toString(), "OTP_VERIFIED", "OTP",
                challenge.id().toString(), "SUCCESS", channel.name(), null);

        return CustomerViews.from(customer);
    }

    private UUID parseCustomerId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found");
        }
    }

    private VerificationChannel parseChannel(String raw) {
        try {
            return VerificationChannel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new com.choco.core.error.ValidationException(ErrorCode.VALIDATION_FAILED,
                    "Unknown verification channel: " + raw);
        }
    }
}
