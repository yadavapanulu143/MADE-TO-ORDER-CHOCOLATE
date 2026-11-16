package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.identity.application.command.VerifyOtpCommand;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.application.fake.InMemoryOtpChallengeRepository;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.domain.otp.OtpChallenge;
import com.choco.identity.application.fake.TestAbuseGuard;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final String CORRECT_CODE = "123456";

    private InMemoryCustomerRepository customers;
    private InMemoryOtpChallengeRepository challenges;
    private List<String> welcomeMessages;
    private Fakes.RecordingEventPublisher events;
    private VerificationService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        challenges = new InMemoryOtpChallengeRepository();
        welcomeMessages = new ArrayList<>();
        events = new Fakes.RecordingEventPublisher();

        customer = Customer.register(UUID.randomUUID(),
                ContactDetails.of("Asha Menon", "asha@example.com", "+919876543210"),
                "hashed", NOW);
        customers.save(customer);

        var otpCodes = new com.choco.identity.domain.port.OtpCodeService() {
            @Override
            public String generateCode() {
                return CORRECT_CODE;
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
        var notifications = new com.choco.identity.application.port.AccountNotificationPort() {
            @Override
            public void welcome(String email, String fullName) {
                welcomeMessages.add(email);
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

        service = new VerificationService(customers, challenges, otpCodes, notifications, audit, events,
                TestAbuseGuard.create(), new Fakes.DirectTransactionRunner(),
                Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z"));
    }

    private void issueChallenge(VerificationChannel channel) {
        challenges.save(OtpChallenge.issue(UUID.randomUUID(), customer.id(), channel.name(),
                "dest", "otp:" + CORRECT_CODE, NOW));
    }

    private VerifyOtpCommand verify(VerificationChannel channel, String code) {
        return new VerifyOtpCommand(customer.id().toString(), channel.name(), code);
    }

    @Test
    void a_correct_code_verifies_the_channel() {
        issueChallenge(VerificationChannel.EMAIL);

        var view = service.verify(verify(VerificationChannel.EMAIL, CORRECT_CODE));

        assertThat(view.emailVerified()).isTrue();
        assertThat(view.mobileVerified()).isFalse();
        assertThat(view.fullyVerified()).isFalse();
    }

    @Test
    void verifying_both_channels_sends_exactly_one_welcome_message() {
        issueChallenge(VerificationChannel.EMAIL);
        issueChallenge(VerificationChannel.MOBILE);

        service.verify(verify(VerificationChannel.EMAIL, CORRECT_CODE));
        assertThat(welcomeMessages).as("not welcome-worthy until fully verified").isEmpty();

        service.verify(verify(VerificationChannel.MOBILE, CORRECT_CODE));

        assertThat(welcomeMessages)
                .as("the welcome must be sent once, on the last verification")
                .containsExactly("asha@example.com");
    }

    @Test
    void an_incorrect_code_is_rejected_and_the_challenge_is_preserved() {
        issueChallenge(VerificationChannel.EMAIL);

        assertThatThrownBy(() -> service.verify(verify(VerificationChannel.EMAIL, "000000")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.OTP_INVALID);

        assertThat(challenges.findLatestActive(customer.id(), "EMAIL").orElseThrow().attempts())
                .as("a failed attempt must be recorded")
                .isEqualTo(1);
    }

    @Test
    void an_expired_code_is_rejected() {
        challenges.save(OtpChallenge.rehydrate(UUID.randomUUID(), customer.id(), "EMAIL", "dest",
                "otp:" + CORRECT_CODE, NOW.minusSeconds(600), NOW.minusSeconds(300), 0, 0, null));

        assertThatThrownBy(() -> service.verify(verify(VerificationChannel.EMAIL, CORRECT_CODE)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void verifying_without_an_active_challenge_is_rejected() {
        assertThatThrownBy(() -> service.verify(verify(VerificationChannel.EMAIL, CORRECT_CODE)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).errorCode())
                .isEqualTo(ErrorCode.OTP_INVALID);
    }

    @Test
    void an_already_verified_channel_cannot_be_verified_again() {
        issueChallenge(VerificationChannel.EMAIL);
        service.verify(verify(VerificationChannel.EMAIL, CORRECT_CODE));
        issueChallenge(VerificationChannel.EMAIL);

        assertThatThrownBy(() -> service.verify(verify(VerificationChannel.EMAIL, CORRECT_CODE)))
                .isInstanceOf(com.choco.core.error.ConflictException.class);
    }

    @Test
    void an_unknown_channel_name_is_a_validation_error_not_a_crash() {
        assertThatThrownBy(() -> service.verify(new VerifyOtpCommand(customer.id().toString(), "FAX", "1")))
                .isInstanceOf(com.choco.core.error.ValidationException.class);
    }

    @Test
    void an_unknown_customer_is_not_found() {
        assertThatThrownBy(() -> service.verify(
                new VerifyOtpCommand(UUID.randomUUID().toString(), "EMAIL", CORRECT_CODE)))
                .isInstanceOf(com.choco.core.error.NotFoundException.class);
    }

    @Test
    void a_non_uuid_customer_id_is_not_found_rather_than_a_crash() {
        assertThatThrownBy(() -> service.verify(new VerifyOtpCommand("not-a-uuid", "EMAIL", "1")))
                .isInstanceOf(com.choco.core.error.NotFoundException.class);
    }

    @Test
    void verification_raises_a_customer_verified_event() {
        issueChallenge(VerificationChannel.EMAIL);

        service.verify(verify(VerificationChannel.EMAIL, CORRECT_CODE));

        assertThat(events.eventsOfType("customer.verified")).hasSize(1);
    }
}
