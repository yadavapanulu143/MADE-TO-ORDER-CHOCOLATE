package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ValidationException;
import com.choco.core.port.IdGenerator;
import com.choco.identity.application.command.RegisterCustomerCommand;
import com.choco.identity.application.dto.RegistrationResult;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.application.fake.InMemoryOtpChallengeRepository;
import com.choco.identity.application.fake.SnapshottingTransactionRunner;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.application.fake.TestAbuseGuard;
import com.choco.test.Fakes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationServiceTest {

    private InMemoryCustomerRepository customers;
    private InMemoryOtpChallengeRepository challenges;
    private List<String> deliveredCodes;
    private List<VerificationChannel> deliveredChannels;
    private Fakes.RecordingEventPublisher events;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        challenges = new InMemoryOtpChallengeRepository();
        deliveredCodes = new ArrayList<>();
        deliveredChannels = new ArrayList<>();
        events = new Fakes.RecordingEventPublisher();

        // A deterministic hasher: the real Argon2id cost is irrelevant to these assertions and
        // would only slow the suite down. The real algorithm is tested in common-security.
        var hasher = new com.choco.core.port.PasswordHasher() {
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
        };
        var policy = new com.choco.core.port.PasswordPolicy() {
            @Override
            public List<String> violations(String rawPassword) {
                return rawPassword != null && rawPassword.length() >= 12 ? List.of() : List.of("too short");
            }
        };
        var otpCodes = new com.choco.identity.domain.port.OtpCodeService() {
            private int counter = 100000;

            @Override
            public String generateCode() {
                return String.valueOf(++counter);
            }

            @Override
            public String hashCode(String code) {
                return "otp:" + code;
            }

            @Override
            public boolean matches(String submittedCode, String storedHash) {
                return storedHash.equals("otp:" + submittedCode);
            }
        };
        var delivery = new com.choco.identity.application.port.OtpDeliveryPort() {
            @Override
            public void deliver(VerificationChannel channel, String destination, String code, String fullName) {
                deliveredChannels.add(channel);
                deliveredCodes.add(code);
            }
        };
        var notifications = new com.choco.identity.application.port.AccountNotificationPort() {
            @Override
            public void welcome(String email, String fullName) {
            }

            @Override
            public void passwordChanged(String email, String fullName) {
            }

            @Override
            public void passwordResetRequested(String email, String fullName, String resetToken) {
            }
        };
        var audit = new com.choco.identity.application.port.AuditLogPort() {
            @Override
            public void record(String actorType, String actorId, String action, String targetType,
                               String targetId, String outcome, String detail, String correlationId) {
            }
        };

        service = new RegistrationService(customers, challenges, hasher, policy, otpCodes, delivery,
                notifications, audit, events, TestAbuseGuard.create(),
                new SnapshottingTransactionRunner(customers, challenges),
                new Fakes.SequentialIdGenerator(), Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z"));
    }

    private static RegisterCustomerCommand command(String email, String mobile) {
        return new RegisterCustomerCommand("Asha Menon", email, mobile, "correct horse battery");
    }

    @Test
    void registers_a_customer_and_issues_both_challenges() {
        RegistrationResult result = service.register(command("asha@example.com", "+919876543210"));

        assertThat(result.customerId()).isNotNull();
        assertThat(customers.count()).isEqualTo(1);
        assertThat(deliveredChannels)
                .as("both email and mobile must be challenged at registration")
                .containsExactlyInAnyOrder(VerificationChannel.EMAIL, VerificationChannel.MOBILE);
        assertThat(deliveredCodes).allSatisfy(code -> assertThat(code).isNotBlank());
    }

    @Test
    void registration_raises_a_customer_registered_event() {
        service.register(command("asha@example.com", "+919876543210"));

        assertThat(events.eventsOfType("customer.registered"))
                .as("the outbox must see exactly one registration event")
                .hasSize(1);
    }

    @Test
    void the_stored_password_is_never_the_raw_password() {
        service.register(command("asha@example.com", "+919876543210"));

        var stored = customers.findByEmail(com.choco.core.domain.EmailAddress.of("asha@example.com"))
                .orElseThrow();

        assertThat(stored.passwordHash())
                .as("a raw password must never reach the database")
                .isNotEqualTo("correct horse battery")
                .startsWith("hashed:");
    }

    @Test
    void rejects_a_duplicate_email() {
        service.register(command("asha@example.com", "+919876543210"));

        assertThatThrownBy(() -> service.register(command("ASHA@example.com", "+919876543211")))
                .as("email comparison must be case-insensitive")
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).errorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void rejects_a_duplicate_mobile() {
        service.register(command("asha@example.com", "+919876543210"));

        assertThatThrownBy(() -> service.register(command("other@example.com", "9876543210")))
                .as("the same number written differently is still a duplicate")
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).errorCode())
                .isEqualTo(ErrorCode.MOBILE_ALREADY_REGISTERED);
    }

    @Test
    void rejects_a_weak_password_before_hashing_it() {
        assertThatThrownBy(() -> service.register(
                new RegisterCustomerCommand("Asha Menon", "asha@example.com", "+919876543210", "short")))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.WEAK_PASSWORD);

        assertThat(customers.count())
                .as("a rejected registration must not be persisted")
                .isZero();
    }

    @Test
    void rejects_a_malformed_email() {
        assertThatThrownBy(() -> service.register(command("not-an-email", "+919876543210")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(customers.count()).isZero();
    }

    @Test
    void rejects_a_malformed_indian_mobile() {
        assertThatThrownBy(() -> service.register(command("asha@example.com", "12345")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(customers.count()).isZero();
    }

    @Test
    void resend_is_throttled_immediately_after_registration() {
        RegistrationResult result = service.register(command("asha@example.com", "+919876543210"));

        assertThatThrownBy(() -> service.resendChallenge(result.customerId().toString(),
                VerificationChannel.EMAIL))
                .as("the resend interval must apply to the freshly issued code")
                .isInstanceOf(com.choco.core.error.BusinessRuleException.class)
                .extracting(e -> ((com.choco.core.error.BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void resend_is_refused_once_the_channel_is_verified() {
        RegistrationResult result = service.register(command("asha@example.com", "+919876543210"));
        var customer = customers.findById(result.customerId()).orElseThrow();
        customer.verify(VerificationChannel.EMAIL, java.time.Instant.parse("2026-03-01T09:00:00Z"));
        customers.save(customer);

        assertThatThrownBy(() -> service.resendChallenge(result.customerId().toString(),
                VerificationChannel.EMAIL))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknown_customer_is_reported_as_not_found() {
        assertThatThrownBy(() -> service.resendChallenge(UUID.randomUUID().toString(),
                VerificationChannel.EMAIL))
                .isInstanceOf(com.choco.core.error.NotFoundException.class);
    }

    @Test
    void a_failure_after_the_customer_write_leaves_no_customer_behind() {
        // Forces the second challenge to fail inside the unit of work. Without a transaction the
        // customer row would already have been committed by the first save, which is exactly the
        // half-registered state that made a retry report EMAIL_ALREADY_REGISTERED.
        var failingChallenges = new InMemoryOtpChallengeRepository() {
            private int saves = 0;

            @Override
            public com.choco.identity.domain.otp.OtpChallenge save(
                    com.choco.identity.domain.otp.OtpChallenge challenge) {
                if (++saves == 2) {
                    throw new IllegalStateException("simulated failure while issuing the second challenge");
                }
                return super.save(challenge);
            }
        };

        var failing = new RegistrationService(customers, failingChallenges, new Hasher(), new Policy(),
                new OtpCodes(), new Delivery(), new Notifications(), new Audit(), events, TestAbuseGuard.create(),
                new SnapshottingTransactionRunner(customers, failingChallenges),
                new Fakes.SequentialIdGenerator(), Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z"));

        assertThatThrownBy(() -> failing.register(command("asha@example.com", "+919876543210")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customers.count())
                .as("a partial failure must not leave a customer who can never be verified")
                .isZero();

        // The decisive check: the address must be free again, so the customer can retry.
        assertThat(customers.existsByEmail(com.choco.core.domain.EmailAddress.of("asha@example.com")))
                .as("the email must not stay permanently taken by a rolled-back attempt")
                .isFalse();
    }

    @Test
    void no_verification_code_is_delivered_when_the_transaction_rolls_back() {
        var failingChallenges = new InMemoryOtpChallengeRepository() {
            @Override
            public com.choco.identity.domain.otp.OtpChallenge save(
                    com.choco.identity.domain.otp.OtpChallenge challenge) {
                throw new IllegalStateException("simulated failure");
            }
        };

        var failing = new RegistrationService(customers, failingChallenges, new Hasher(), new Policy(),
                new OtpCodes(), new Delivery(), new Notifications(), new Audit(), events, TestAbuseGuard.create(),
                new SnapshottingTransactionRunner(customers, failingChallenges),
                new Fakes.SequentialIdGenerator(), Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z"));

        assertThatThrownBy(() -> failing.register(command("asha@example.com", "+919876543210")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(deliveredCodes)
                .as("a code must never be sent for an account that was rolled back")
                .isEmpty();
    }

    private static final class Hasher implements com.choco.core.port.PasswordHasher {
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

    private static final class Policy implements com.choco.core.port.PasswordPolicy {
        @Override
        public List<String> violations(String rawPassword) {
            return rawPassword != null && rawPassword.length() >= 12 ? List.of() : List.of("too short");
        }
    }

    private static final class OtpCodes implements com.choco.identity.domain.port.OtpCodeService {
        private int counter = 200000;

        @Override
        public String generateCode() {
            return String.valueOf(++counter);
        }

        @Override
        public String hashCode(String code) {
            return "otp:" + code;
        }

        @Override
        public boolean matches(String submittedCode, String storedHash) {
            return storedHash.equals("otp:" + submittedCode);
        }
    }

    private static final class Delivery implements com.choco.identity.application.port.OtpDeliveryPort {
        @Override
        public void deliver(VerificationChannel channel, String destination, String code, String fullName) {
        }
    }

    private static final class Notifications implements com.choco.identity.application.port.AccountNotificationPort {
        @Override
        public void welcome(String email, String fullName) {
        }

        @Override
        public void passwordChanged(String email, String fullName) {
        }

        @Override
        public void passwordResetRequested(String email, String fullName, String resetToken) {
        }
    }

    private static final class Audit implements com.choco.identity.application.port.AuditLogPort {
        @Override
        public void record(String actorType, String actorId, String action, String targetType,
                           String targetId, String outcome, String detail, String correlationId) {
        }
    }
}
