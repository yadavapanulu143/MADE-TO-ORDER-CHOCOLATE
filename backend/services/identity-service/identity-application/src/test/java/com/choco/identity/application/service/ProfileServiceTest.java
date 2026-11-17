package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.identity.application.command.ChangeContactCommand;
import com.choco.identity.application.command.UpdateProfileCommand;
import com.choco.identity.application.dto.CustomerView;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.application.fake.InMemoryOtpChallengeRepository;
import com.choco.identity.application.fake.SnapshottingTransactionRunner;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.application.port.OtpDeliveryPort;
import com.choco.identity.domain.customer.ContactDetails;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.customer.VerificationChannel;
import com.choco.identity.domain.port.OtpCodeService;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests self-service profile editing.
 *
 * <p>The behaviour that carries risk here is a contact change. Moving an account onto an email or
 * mobile the customer does not control must not leave the channel marked verified, so
 * {@link ContactChange} asserts the flag is cleared and a fresh code is delivered to the new
 * destination, not the old one.
 */
@DisplayName("ProfileService")
class ProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private InMemoryCustomerRepository customers;
    private InMemoryOtpChallengeRepository challenges;
    private List<String> deliveredDestinations;
    private List<VerificationChannel> deliveredChannels;
    private List<String> deliveredCodes;
    private List<String[]> auditRecords;
    private Fakes.RecordingEventPublisher events;
    private ProfileService service;
    private final UUID asha = UUID.randomUUID();
    private final UUID bhavna = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        challenges = new InMemoryOtpChallengeRepository();
        deliveredDestinations = new ArrayList<>();
        deliveredChannels = new ArrayList<>();
        deliveredCodes = new ArrayList<>();
        auditRecords = new ArrayList<>();

        OtpDeliveryPort delivery = (channel, destination, code, fullName) -> {
            deliveredChannels.add(channel);
            deliveredDestinations.add(destination);
            deliveredCodes.add(code);
        };
        AuditLogPort auditLog = (actorType, actorId, action, targetType, targetId, outcome, detail, meta) ->
                auditRecords.add(new String[] {action, targetId, outcome});

        OtpCodeService otpCodes = new OtpCodeService() {
            @Override
            public String generateCode() {
                return "123456";
            }

            @Override
            public String hashCode(String code) {
                return "hash:" + code;
            }

            @Override
            public boolean matches(String submittedCode, String storedHash) {
                return storedHash.equals("hash:" + submittedCode);
            }
        };

        events = new Fakes.RecordingEventPublisher();
        service = new ProfileService(customers, challenges, otpCodes, delivery, auditLog,
                events,
                new SnapshottingTransactionRunner(customers, challenges),
                new Fakes.SequentialIdGenerator(), new Fakes.FixedClock(NOW));
    }

    private Customer seedCustomer(UUID id, String email, String mobile, boolean verified) {
        Customer customer = Customer.register(id, ContactDetails.of("Asha Rao", email, mobile),
                "$argon2id$hash", NOW);
        if (verified) {
            customer.verify(VerificationChannel.EMAIL, NOW);
            customer.verify(VerificationChannel.MOBILE, NOW);
        }
        customers.save(customer);
        return customer;
    }

    @Nested
    @DisplayName("name update")
    class NameUpdate {

        @Test
        void changes_the_display_name() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            CustomerView view = service.updateProfile(asha, new UpdateProfileCommand("Asha R Menon"));

            assertThat(view.fullName()).isEqualTo("Asha R Menon");
            assertThat(customers.findById(asha).orElseThrow().contact().fullName())
                    .isEqualTo("Asha R Menon");
        }

        @Test
        void keeps_the_verified_flags() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            CustomerView view = service.updateProfile(asha, new UpdateProfileCommand("Asha R Menon"));

            assertThat(view.fullyVerified())
                    .as("a name change must not disturb verification")
                    .isTrue();
        }

        @Test
        void rejects_a_blank_name() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            assertThatThrownBy(() -> service.updateProfile(asha, new UpdateProfileCommand("  ")))
                    .isInstanceOf(ValidationException.class)
                    .extracting(e -> ((ValidationException) e).errorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        void rejects_an_unknown_customer() {
            assertThatThrownBy(() -> service.updateProfile(UUID.randomUUID(),
                    new UpdateProfileCommand("Nobody")))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("cannot touch another customer's profile")
        void cannotTouchAnotherCustomer() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);
            seedCustomer(bhavna, "bhavna@example.com", "9876543211", true);

            service.updateProfile(asha, new UpdateProfileCommand("Asha Renamed"));

            assertThat(customers.findById(bhavna).orElseThrow().contact().fullName())
                    .as("editing one profile must not rename another")
                    .isEqualTo("Asha Rao");
        }

        @Test
        void records_an_audit_entry() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            service.updateProfile(asha, new UpdateProfileCommand("Asha R Menon"));

            assertThat(auditRecords)
                    .anySatisfy(record -> assertThat(record[0]).isEqualTo("PROFILE_UPDATED"));
        }
    }

    @Nested
    @DisplayName("contact change")
    class ContactChange {

        @Test
        void changing_the_email_clears_its_verification() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            CustomerView view = service.changeContact(asha,
                    new ChangeContactCommand("new@example.com", null));

            assertThat(view.email()).isEqualTo("new@example.com");
            assertThat(view.emailVerified())
                    .as("an unproven address must not stay verified")
                    .isFalse();
            assertThat(view.mobileVerified()).isTrue();
            assertThat(view.fullyVerified()).isFalse();
        }

        @Test
        void changing_the_mobile_clears_its_verification() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            CustomerView view = service.changeContact(asha,
                    new ChangeContactCommand(null, "9123456780"));

            assertThat(view.mobile()).isEqualTo("9123456780");
            assertThat(view.mobileVerified()).isFalse();
            assertThat(view.emailVerified()).isTrue();
        }

        @Test
        @DisplayName("the new code goes to the new destination, not the old one")
        void codeGoesToTheNewDestination() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            service.changeContact(asha, new ChangeContactCommand("new@example.com", null));

            assertThat(deliveredDestinations).containsExactly("new@example.com");
            assertThat(deliveredChannels).containsExactly(VerificationChannel.EMAIL);
            assertThat(deliveredCodes).isNotEmpty();
        }

        @Test
        @DisplayName("a contact change is published so the notification projection re-addresses the customer")
        void contactChangeIsPublished() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            service.changeContact(asha, new ChangeContactCommand("new@example.com", null));

            assertThat(events.eventsOfType("customer.contact-changed"))
                    .as("without this event the notification service keeps the old address")
                    .singleElement()
                    .satisfies(event -> {
                        var changed = (com.choco.identity.domain.event.CustomerContactChanged) event;
                        assertThat(changed.email()).isEqualTo("new@example.com");
                        assertThat(changed.mobile()).isEqualTo("9876543210");
                    });
        }

        @Test
        @DisplayName("resubmitting the current address publishes nothing")
        void resubmittingTheSameAddressPublishesNothing() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            service.changeContact(asha, new ChangeContactCommand("asha@example.com", "9876543210"));

            assertThat(events.eventsOfType("customer.contact-changed"))
                    .as("an unchanged address must not churn the projection or send a code")
                    .isEmpty();
        }

        @Test
        void changing_both_channels_delivers_two_codes() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            service.changeContact(asha, new ChangeContactCommand("new@example.com", "9123456780"));

            assertThat(deliveredChannels)
                    .containsExactlyInAnyOrder(VerificationChannel.EMAIL, VerificationChannel.MOBILE);
            assertThat(challenges.count()).isEqualTo(2);
        }

        @Test
        void a_challenge_is_persisted_for_the_new_destination() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            service.changeContact(asha, new ChangeContactCommand("new@example.com", null));

            assertThat(challenges.findLatestActive(asha, "EMAIL")).isPresent();
            assertThat(challenges.findLatestActive(asha, "EMAIL").orElseThrow().destination())
                    .isEqualTo("new@example.com");
        }

        @Test
        void rejects_an_email_already_used_by_another_customer() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);
            seedCustomer(bhavna, "bhavna@example.com", "9876543211", true);

            assertThatThrownBy(() -> service.changeContact(asha,
                    new ChangeContactCommand("bhavna@example.com", null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).errorCode())
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        @Test
        void rejects_a_mobile_already_used_by_another_customer() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);
            seedCustomer(bhavna, "bhavna@example.com", "9876543211", true);

            assertThatThrownBy(() -> service.changeContact(asha,
                    new ChangeContactCommand(null, "9876543211")))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).errorCode())
                    .isEqualTo(ErrorCode.MOBILE_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("a rejected change leaves the account untouched")
        void rejectedChangeLeavesNoTrace() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);
            seedCustomer(bhavna, "bhavna@example.com", "9876543211", true);

            assertThatThrownBy(() -> service.changeContact(asha,
                    new ChangeContactCommand("bhavna@example.com", null)))
                    .isInstanceOf(ConflictException.class);

            Customer stored = customers.findById(asha).orElseThrow();
            assertThat(stored.contact().email().value())
                    .as("a rejected change must not move the account")
                    .isEqualTo("asha@example.com");
            assertThat(stored.fullyVerified()).isTrue();
            assertThat(deliveredDestinations).isEmpty();
        }

        @Test
        @DisplayName("re-submitting the same email is a no-op, not a self-collision")
        void resubmittingTheSameEmailIsANoOp() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            CustomerView view = service.changeContact(asha,
                    new ChangeContactCommand("asha@example.com", null));

            assertThat(view.emailVerified())
                    .as("an unchanged address must keep its verification")
                    .isTrue();
            assertThat(deliveredDestinations).isEmpty();
        }

        @Test
        void rejects_a_request_with_neither_field() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            assertThatThrownBy(() -> service.changeContact(asha, new ChangeContactCommand(null, null)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void rejects_a_malformed_email() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            assertThatThrownBy(() -> service.changeContact(asha,
                    new ChangeContactCommand("not-an-email", null)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void rejects_a_malformed_mobile() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            assertThatThrownBy(() -> service.changeContact(asha,
                    new ChangeContactCommand(null, "12345")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("cannot change another customer's contact details")
        void cannotChangeAnotherCustomer() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);
            seedCustomer(bhavna, "bhavna@example.com", "9876543211", true);

            service.changeContact(asha, new ChangeContactCommand("asha.new@example.com", null));

            assertThat(customers.findById(bhavna).orElseThrow().contact().email().value())
                    .as("one customer's change must not touch another account")
                    .isEqualTo("bhavna@example.com");
        }

        @Test
        void records_an_audit_entry() {
            seedCustomer(asha, "asha@example.com", "9876543210", true);

            service.changeContact(asha, new ChangeContactCommand("new@example.com", null));

            assertThat(auditRecords)
                    .anySatisfy(record -> assertThat(record[0]).isEqualTo("CONTACT_CHANGED"));
        }
    }
}
