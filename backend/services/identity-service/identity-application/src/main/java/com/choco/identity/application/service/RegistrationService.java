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
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.PasswordPolicy;
import com.choco.core.port.TransactionRunner;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
 *
 * <p>Every persistent write happens inside one transaction. A customer row without its challenges is
 * not a valid state — the account could never be verified and the address would stay permanently
 * taken — so a failure part-way through must leave nothing behind. Delivery of the codes happens
 * <em>after</em> that transaction commits: sending from inside it would mail a customer whose account
 * a later rollback erases, and the recipient cannot tell the difference.
 */
public class RegistrationService {

    private final CustomerRepository customerRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final OtpCodeService otpCodeService;
    private final OtpDeliveryPort otpDelivery;
    private final AccountNotificationPort notifications;
    private final AuditLogPort auditLog;
    private final EventPublisher eventPublisher;
    private final AbuseGuard abuseGuard;
    private final TransactionRunner transactionRunner;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public RegistrationService(CustomerRepository customerRepository,
                               OtpChallengeRepository otpChallengeRepository,
                               PasswordHasher passwordHasher,
                               PasswordPolicy passwordPolicy,
                               OtpCodeService otpCodeService,
                               OtpDeliveryPort otpDelivery,
                               AccountNotificationPort notifications,
                               AuditLogPort auditLog,
                               EventPublisher eventPublisher,
                               AbuseGuard abuseGuard,
                               TransactionRunner transactionRunner,
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
        this.abuseGuard = abuseGuard;
        this.transactionRunner = transactionRunner;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public RegistrationResult register(RegisterCustomerCommand command) {
        // Limits account creation per source address, so the signup form cannot be used to mass-create
        // accounts or as a way to send verification mail to arbitrary addresses.
        abuseGuard.checkRegistration(command.ipAddress());

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

        // Codes are generated before the write so the raw values are available to deliver once the
        // transaction commits; only their hashes are persisted.
        String emailCode = otpCodeService.generateCode();
        String mobileCode = otpCodeService.generateCode();
        OtpChallenge emailChallenge = challenge(customer, VerificationChannel.EMAIL,
                contact.email().value(), emailCode, now);
        OtpChallenge mobileChallenge = challenge(customer, VerificationChannel.MOBILE,
                contact.mobile().value(), mobileCode, now);

        transactionRunner.execute(() -> {
            customerRepository.save(customer);
            otpChallengeRepository.save(emailChallenge);
            otpChallengeRepository.save(mobileChallenge);
            eventPublisher.publishAll(customer.pullDomainEvents());
        });

        deliver(emailChallenge, contact.email().value(), emailCode, contact.fullName());
        deliver(mobileChallenge, contact.mobile().value(), mobileCode, contact.fullName());
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

        // Limits resends across challenges, complementing the per-challenge throttle in the domain.
        // The domain throttle stops rapid repeat requests; this stops a slow trickle over hours.
        abuseGuard.checkOtpResend(customer.id().toString());

        if (customer.isVerified(channel)) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "This " + channel.name().toLowerCase() + " is already verified");
        }

        String destination = destinationFor(customer, channel);
        String code = otpCodeService.generateCode();

        var existing = otpChallengeRepository.findLatestActive(customer.id(), channel.name());
        OtpChallenge toPersist;
        if (existing.isPresent()) {
            OtpChallenge current = existing.get();
            current.assertResendAllowed(now);
            toPersist = current.withNewCode(otpCodeService.hashCode(code), now);
        } else {
            toPersist = challenge(customer, channel, destination, code, now);
        }

        transactionRunner.execute(() -> otpChallengeRepository.save(toPersist));

        deliver(toPersist, destination, code, customer.contact().fullName());
        auditLog.record("CUSTOMER", customer.id().toString(), "OTP_RESENT", "OTP",
                toPersist.id().toString(), "SUCCESS", channel.name(), null);
    }

    private OtpChallenge challenge(Customer customer, VerificationChannel channel, String destination,
                                   String rawCode, Instant now) {
        return OtpChallenge.issue(idGenerator.nextId(), customer.id(), channel.name(), destination,
                otpCodeService.hashCode(rawCode), now);
    }

    /**
     * Runs after the transaction commits, so a code is never sent for an account that rolled back.
     * Delivery is best-effort by contract: a mail outage must not undo a completed registration, and
     * the customer can request a new code.
     */
    private void deliver(OtpChallenge challenge, String destination, String code, String fullName) {
        otpDelivery.deliver(VerificationChannel.valueOf(challenge.channel()), destination, code, fullName);
        auditLog.record("CUSTOMER", challenge.customerId().toString(), "OTP_ISSUED", "OTP",
                challenge.id().toString(), "SUCCESS", challenge.channel(), null);
    }

    private String destinationFor(Customer customer, VerificationChannel channel) {
        return channel == VerificationChannel.EMAIL
                ? customer.contact().email().value()
                : customer.contact().mobile().value();
    }

    private Customer requireCustomer(String customerIdRaw) {
        return customerRepository.findById(UUID.fromString(customerIdRaw))
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }

    /** Exposed so a welcome message can be sent once both channels are verified. */
    AccountNotificationPort notifications() {
        return notifications;
    }
}
