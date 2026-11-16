package com.choco.identity.application.service;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.identity.application.command.ChangeContactCommand;
import com.choco.identity.application.command.UpdateProfileCommand;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.OtpDeliveryPort;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.domain.otp.OtpChallenge;
import com.choco.identity.domain.port.CustomerRepository;
import com.choco.identity.domain.port.OtpChallengeRepository;
import com.choco.identity.domain.port.OtpCodeService;
import java.time.Instant;
import java.util.UUID;

/**
 * Updates a customer's own profile.
 *
 * <p>The customer id always comes from the caller, which the web adapter reads from the authenticated
 * token, so there is no path in this class that can write another account.
 *
 * <p>A contact change is treated as a security event, not a field edit. Changing an email or mobile
 * clears the verification for that channel and issues a fresh code to the new destination, because
 * the new address has not been proven to belong to this customer. Without that step a customer could
 * move an account onto an address they do not control and keep the "verified" badge, which would let
 * them receive password-reset mail for a mailbox that is not theirs. Uniqueness is re-checked against
 * the whole table before the write, so two accounts cannot converge on the same address.
 *
 * <p>Codes are delivered after the transaction commits, matching registration: sending from inside
 * the transaction would mail a customer whose change a later rollback erases.
 */
public class ProfileService {

    private final CustomerRepository customerRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final OtpCodeService otpCodeService;
    private final OtpDeliveryPort otpDelivery;
    private final AuditLogPort auditLog;
    private final EventPublisher eventPublisher;
    private final TransactionRunner transactionRunner;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ProfileService(CustomerRepository customerRepository,
                          OtpChallengeRepository otpChallengeRepository,
                          OtpCodeService otpCodeService,
                          OtpDeliveryPort otpDelivery,
                          AuditLogPort auditLog,
                          EventPublisher eventPublisher,
                          TransactionRunner transactionRunner,
                          IdGenerator idGenerator,
                          Clock clock) {
        this.customerRepository = customerRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.otpCodeService = otpCodeService;
        this.otpDelivery = otpDelivery;
        this.auditLog = auditLog;
        this.eventPublisher = eventPublisher;
        this.transactionRunner = transactionRunner;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** Changes the display name only; verification state is untouched. */
    public CustomerView updateProfile(UUID customerId, UpdateProfileCommand command) {
        Instant now = clock.now();
        Customer customer = require(customerId);

        try {
            customer.changeFullName(command.fullName(), now);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }

        transactionRunner.execute(() -> {
            customerRepository.save(customer);
            eventPublisher.publishAll(customer.pullDomainEvents());
        });

        auditLog.record("CUSTOMER", customerId.toString(), "PROFILE_UPDATED", "CUSTOMER",
                customerId.toString(), "SUCCESS", null, null);

        return CustomerViews.from(customer);
    }

    /**
     * Changes the email and/or mobile, revoking verification for each channel that actually changed.
     *
     * <p>A new code is issued for every changed channel and delivered to the new destination. A value
     * equal to the current one is treated as a no-op for that channel, so re-submitting the same form
     * does not spuriously revoke a working verification.
     */
    public CustomerView changeContact(UUID customerId, ChangeContactCommand command) {
        Instant now = clock.now();
        Customer customer = require(customerId);

        EmailAddress newEmail = parseEmail(command.email());
        PhoneNumber newMobile = parseMobile(command.mobile());

        if (newEmail == null && newMobile == null) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED,
                    "Provide a new email address or mobile number to change");
        }

        boolean emailChanged = newEmail != null && !newEmail.equals(customer.contact().email());
        boolean mobileChanged = newMobile != null && !newMobile.equals(customer.contact().mobile());

        // Uniqueness is checked only for the channels that actually change, so resubmitting the
        // current address does not collide with the customer's own row.
        if (emailChanged && customerRepository.existsByEmail(newEmail)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account already exists for this email address");
        }
        if (mobileChanged && customerRepository.existsByMobile(newMobile)) {
            throw new ConflictException(ErrorCode.MOBILE_ALREADY_REGISTERED,
                    "An account already exists for this mobile number");
        }

        if (!emailChanged && !mobileChanged) {
            return CustomerViews.from(customer);
        }

        if (emailChanged) {
            customer.changeEmail(newEmail, now);
        }
        if (mobileChanged) {
            customer.changeMobile(newMobile, now);
        }

        // Codes are generated before the write so the raw values survive the commit and can be
        // delivered afterwards; only their hashes are persisted.
        String emailCode = emailChanged ? otpCodeService.generateCode() : null;
        String mobileCode = mobileChanged ? otpCodeService.generateCode() : null;
        OtpChallenge emailChallenge = emailChanged
                ? challenge(customer, VerificationChannel.EMAIL, newEmail.value(), emailCode, now) : null;
        OtpChallenge mobileChallenge = mobileChanged
                ? challenge(customer, VerificationChannel.MOBILE, newMobile.value(), mobileCode, now) : null;

        transactionRunner.execute(() -> {
            customerRepository.save(customer);
            if (emailChallenge != null) {
                otpChallengeRepository.save(emailChallenge);
            }
            if (mobileChallenge != null) {
                otpChallengeRepository.save(mobileChallenge);
            }
            eventPublisher.publishAll(customer.pullDomainEvents());
        });

        if (emailChallenge != null) {
            deliver(emailChallenge, newEmail.value(), emailCode, customer.contact().fullName());
        }
        if (mobileChallenge != null) {
            deliver(mobileChallenge, newMobile.value(), mobileCode, customer.contact().fullName());
        }

        auditLog.record("CUSTOMER", customerId.toString(), "CONTACT_CHANGED", "CUSTOMER",
                customerId.toString(), "SUCCESS",
                "emailChanged=" + emailChanged + ", mobileChanged=" + mobileChanged, null);

        return CustomerViews.from(customer);
    }

    private OtpChallenge challenge(Customer customer, VerificationChannel channel, String destination,
                                   String rawCode, Instant now) {
        return OtpChallenge.issue(idGenerator.nextId(), customer.id(), channel.name(), destination,
                otpCodeService.hashCode(rawCode), now);
    }

    private void deliver(OtpChallenge challenge, String destination, String code, String fullName) {
        otpDelivery.deliver(VerificationChannel.valueOf(challenge.channel()), destination, code, fullName);
        auditLog.record("CUSTOMER", challenge.customerId().toString(), "OTP_ISSUED", "OTP",
                challenge.id().toString(), "SUCCESS", challenge.channel(), null);
    }

    /** Returns null for a null or blank input, meaning "leave unchanged". */
    private static EmailAddress parseEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EmailAddress.of(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Enter a valid email address");
        }
    }

    private static PhoneNumber parseMobile(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PhoneNumber.of(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Enter a valid 10-digit Indian mobile number");
        }
    }

    private Customer require(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }
}
