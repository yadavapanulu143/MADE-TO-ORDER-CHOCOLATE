package com.choco.identity.application.service;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.identity.application.command.RegisterCustomerCommand;
import com.choco.identity.application.dto.RegistrationResult;
import com.choco.identity.application.port.AccountNotificationPort;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.OtpDeliveryPort;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.domain.otp.OtpChallenge;
import com.choco.identity.domain.port.CustomerRepository;
import com.choco.identity.domain.port.OtpChallengeRepository;
import com.choco.identity.domain.port.OtpCodeService;
import com.choco.identity.domain.port.PasswordHasher;
import com.choco.identity.domain.port.PasswordPolicyPort;
import java.time.Instant;
import java.util.List;

/**
 * Registers a customer and starts both verification challenges.
 *
 * <p>The order of operations matters. Uniqueness is checked before hashing, because hashing is
 * deliberately expensive and there is no reason to spend that cost on a request that will be
 * rejected. The password policy is checked before hashing for the same reason.
 *
 * <p>The uniqueness check is a fast pre-check for a friendly error message. The real guarantee is a
 * unique index in the database: two simultaneous registrations of the same email can both pass the
 * check, and one of them must then be rejected by the constraint. That path is handled in the
 * adapter and reported as the same conflict.
 */
public class RegistrationService {

    private final CustomerRepository customerRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyPort passwordPolicy;
    private final OtpCodeService otpCodeService;
    private final OtpDeliveryPort otpDelivery;
    private final AccountNotificationPort notifications;
    private final AuditLogPort auditLog;
    private final EventPublisher eventPublisher;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public RegistrationService(CustomerRepository customerRepository,
                               OtpChallengeRepository otpChallengeRepository,
                               PasswordHasher passwordHasher,
                               PasswordPolicyPort passwordPolicy,
                               OtpCodeService otpCodeService,
                               OtpDeliveryPort otpDelivery,
                               AccountNotificationPort notifications,
                               AuditLogPort auditLog,
                               EventPublisher eventPublisher,
                               IdGenerator idGenerator,
                               Clock clock) {
        this.customerRepository = customerRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.otpCodeService = otpCodeService;
        this.otpDelivery = otpDelivery;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.eventPublisher = eventPublisher;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public RegistrationResult register(RegisterCustomerCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        PhoneNumber mobile = PhoneNumber.of(command.mobile());

        if (customerRepository.existsByEmail(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account already exists for this email address");
        }
        if (customerRepository.existsByMobile(mobile)) {
            throw new ConflictException(ErrorCode.MOBILE_ALREADY_REGISTERED,
                    "An account already exists for this mobile number");
        }

        List<String> violations = passwordPolicy.violations(command.password());
        if (!violations.isEmpty()) {
            throw new ValidationException(ErrorCode.WEAK_PASSWORD, String.join("; ", violations));
        }

        Instant now = clock.now();
        ContactDetails contact = ContactDetails.of(command.fullName(), command.email(), command.mobile());
        Customer customer = Customer.register(idGenerator.nextId(), contact,
                passwordHasher.hash(command.password()), now);
        customerRepository.save(customer);

        issueChallenge(customer, VerificationChannel.EMAIL, contact.email().value(), now);
        issueChallenge(customer, VerificationChannel.MOBILE, contact.mobile().value(), now);

        eventPublisher.publishAll(customer.pullDomainEvents());
        auditLog.record("CUSTOMER", customer.id().toString(), "CUSTOMER_REGISTERED", "CUSTOMER",
                customer.id().toString(), "SUCCESS", null, null);

        return new RegistrationResult(customer.id(), contact.email().value(), contact.mobile().value());
    }

    /**
     * Re-issues a challenge for a channel, respecting the resend throttle.
     *
     * <p>A resend creates a new challenge rather than mutating the previous one once the throttle
     * window has passed, so the attempt counter resets and a stale code cannot be replayed.
     */
    public void resendChallenge(String customerIdRaw, VerificationChannel channel) {
        Customer customer = requireCustomer(customerIdRaw);
        Instant now = clock.now();

        if (customer.isVerified(channel)) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "This " + channel.name().toLowerCase() + " is already verified");
        }

        var existing = otpChallengeRepository.findLatestActive(customer.id(), channel.name());
        if (existing.isPresent()) {
            OtpChallenge current = existing.get();
            current.assertResendAllowed(now);
            String code = otpCodeService.generateCode();
            OtpChallenge renewed = current.withNewCode(otpCodeService.hashCode(code), now);
            otpChallengeRepository.save(renewed);
            otpDelivery.deliver(channel, destinationFor(customer, channel), code, customer.contact().fullName());
            auditLog.record("CUSTOMER", customer.id().toString(), "OTP_RESENT", "OTP",
                    renewed.id().toString(), "SUCCESS", channel.name(), null);
            return;
        }

        issueChallenge(customer, channel, destinationFor(customer, channel), now);
    }

    private void issueChallenge(Customer customer, VerificationChannel channel, String destination, Instant now) {
        String code = otpCodeService.generateCode();
        OtpChallenge challenge = OtpChallenge.issue(idGenerator.nextId(), customer.id(), channel.name(),
                destination, otpCodeService.hashCode(code), now);
        otpChallengeRepository.save(challenge);
        otpDelivery.deliver(channel, destination, code, customer.contact().fullName());
        auditLog.record("CUSTOMER", customer.id().toString(), "OTP_ISSUED", "OTP",
                challenge.id().toString(), "SUCCESS", channel.name(), null);
    }

    private String destinationFor(Customer customer, VerificationChannel channel) {
        return channel == VerificationChannel.EMAIL
                ? customer.contact().email().value()
                : customer.contact().mobile().value();
    }

    private Customer requireCustomer(String customerIdRaw) {
        return customerRepository.findById(java.util.UUID.fromString(customerIdRaw))
                .orElseThrow(() -> new com.choco.core.error.NotFoundException(
                        ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }

    /** Exposed so a welcome message can be sent once both channels are verified. */
    AccountNotificationPort notifications() {
        return notifications;
    }
}
