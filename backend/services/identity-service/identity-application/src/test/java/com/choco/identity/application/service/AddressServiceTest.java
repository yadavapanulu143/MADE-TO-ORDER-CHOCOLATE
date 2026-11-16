package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.identity.application.command.CreateAddressCommand;
import com.choco.identity.application.command.UpdateAddressCommand;
import com.choco.identity.application.dto.AddressView;
import com.choco.identity.application.fake.InMemoryAddressRepository;
import com.choco.identity.application.fake.InMemoryCustomerRepository;
import com.choco.identity.application.fake.InMemoryOtpChallengeRepository;
import com.choco.identity.application.fake.SnapshottingTransactionRunner;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.test.Fakes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests address management, with the emphasis on ownership.
 *
 * <p>The security requirement is that a customer can never reach another customer's address. That is
 * asserted directly in {@link Ownership} rather than inferred from the happy path: a service that
 * skipped the ownership check entirely would pass every other test in this class.
 */
@DisplayName("AddressService")
class AddressServiceTest {

    private InMemoryAddressRepository addresses;
    private List<String[]> auditRecords;
    private AddressService service;
    private final UUID asha = UUID.randomUUID();
    private final UUID bhavna = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        addresses = new InMemoryAddressRepository();
        auditRecords = new ArrayList<>();
        AuditLogPort auditLog = (actorType, actorId, action, targetType, targetId, outcome, detail, meta) ->
                auditRecords.add(new String[] {action, targetId, outcome});

        service = new AddressService(addresses, auditLog,
                new SnapshottingTransactionRunner(new InMemoryCustomerRepository(),
                        new InMemoryOtpChallengeRepository(), addresses),
                new Fakes.SequentialIdGenerator(),
                Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z"));
    }

    private CreateAddressCommand command(String name) {
        return new CreateAddressCommand(name, "9876543210", "12 Cocoa Lane", "Near the park",
                "Bengaluru", "Karnataka", "560001", "Ring the bell twice", false);
    }

    private CreateAddressCommand defaultCommand(String name) {
        return new CreateAddressCommand(name, "9876543210", "12 Cocoa Lane", null, "Bengaluru",
                "Karnataka", "560001", null, true);
    }

    @Nested
    @DisplayName("adding")
    class Adding {

        @Test
        void stores_an_address_for_the_customer() {
            AddressView created = service.add(asha, command("Asha Rao"));

            assertThat(created.recipientName()).isEqualTo("Asha Rao");
            assertThat(created.pincode()).isEqualTo("560001");
            assertThat(addresses.countByCustomerId(asha)).isEqualTo(1);
        }

        @Test
        @DisplayName("the first address becomes the default automatically")
        void firstAddressBecomesDefault() {
            AddressView created = service.add(asha, command("Asha Rao"));

            assertThat(created.isDefault()).isTrue();
        }

        @Test
        void a_second_address_does_not_become_default_on_its_own() {
            service.add(asha, command("Asha Rao"));

            AddressView second = service.add(asha, command("Asha Rao"));

            assertThat(second.isDefault()).isFalse();
        }

        @Test
        void an_explicit_default_replaces_the_previous_default() {
            service.add(asha, command("Asha Rao"));

            AddressView second = service.add(asha, defaultCommand("Asha Rao"));

            assertThat(second.isDefault()).isTrue();
            assertThat(addresses.findByCustomerId(asha).stream().filter(a -> a.isDefault()).count())
                    .as("exactly one address may be the default")
                    .isEqualTo(1);
        }

        @Test
        void the_per_customer_limit_is_enforced() {
            for (int i = 0; i < 10; i++) {
                service.add(asha, command("Asha Rao"));
            }

            assertThatThrownBy(() -> service.add(asha, command("Asha Rao")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("at most 10");
        }

        @Test
        void a_malformed_mobile_number_is_rejected() {
            CreateAddressCommand bad = new CreateAddressCommand("Asha Rao", "12345", "12 Cocoa Lane",
                    null, "Bengaluru", "Karnataka", "560001", null, false);

            assertThatThrownBy(() -> service.add(asha, bad))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void a_malformed_pincode_is_rejected() {
            CreateAddressCommand bad = new CreateAddressCommand("Asha Rao", "9876543210", "12 Cocoa Lane",
                    null, "Bengaluru", "Karnataka", "0123", null, false);

            assertThatThrownBy(() -> service.add(asha, bad))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void the_action_is_audited() {
            service.add(asha, command("Asha Rao"));

            assertThat(auditRecords).anySatisfy(record -> assertThat(record[0]).isEqualTo("ADDRESS_ADDED"));
        }
    }

    @Nested
    @DisplayName("listing and reading")
    class Reading {

        @Test
        void lists_only_the_callers_addresses() {
            service.add(asha, command("Asha Rao"));
            service.add(bhavna, command("Bhavna Rao"));

            assertThat(service.list(asha)).hasSize(1);
            assertThat(service.list(bhavna)).hasSize(1);
        }

        @Test
        void the_default_address_is_listed_first() {
            service.add(asha, command("Asha Rao"));
            service.add(asha, defaultCommand("Asha Rao"));

            assertThat(service.list(asha).getFirst().isDefault()).isTrue();
        }

        @Test
        void reads_an_address_the_caller_owns() {
            AddressView created = service.add(asha, command("Asha Rao"));

            assertThat(service.get(asha, created.id()).id()).isEqualTo(created.id());
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("a customer cannot read another customer's address")
        void cannotReadAnotherCustomersAddress() {
            AddressView ashas = service.add(asha, command("Asha Rao"));

            assertThatThrownBy(() -> service.get(bhavna, ashas.id()))
                    .as("reported as not found, so the response does not confirm the id exists")
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("a customer cannot update another customer's address")
        void cannotUpdateAnotherCustomersAddress() {
            AddressView ashas = service.add(asha, command("Asha Rao"));

            assertThatThrownBy(() -> service.update(bhavna, ashas.id(),
                    new UpdateAddressCommand("Mallory", "9812345678", "99 Cacao Road", null,
                            "Mysuru", "Karnataka", "570001", null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("a customer cannot delete another customer's address")
        void cannotDeleteAnotherCustomersAddress() {
            AddressView ashas = service.add(asha, command("Asha Rao"));

            assertThatThrownBy(() -> service.delete(bhavna, ashas.id()))
                    .isInstanceOf(NotFoundException.class);

            assertThat(addresses.countByCustomerId(asha))
                    .as("a rejected delete must leave the address in place")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("a customer cannot make another customer's address the default")
        void cannotChangeAnotherCustomersDefault() {
            AddressView ashas = service.add(asha, command("Asha Rao"));

            assertThatThrownBy(() -> service.makeDefault(bhavna, ashas.id()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("an attempted cross-customer update leaves the address unchanged")
        void crossCustomerUpdateLeavesNoTrace() {
            AddressView ashas = service.add(asha, command("Asha Rao"));

            assertThatThrownBy(() -> service.update(bhavna, ashas.id(),
                    new UpdateAddressCommand("Mallory", "9812345678", "99 Cacao Road", null,
                            "Mysuru", "Karnataka", "570001", null)))
                    .isInstanceOf(NotFoundException.class);

            assertThat(service.get(asha, ashas.id()).recipientName()).isEqualTo("Asha Rao");
        }
    }

    @Nested
    @DisplayName("updating")
    class Updating {

        @Test
        void updates_the_editable_fields() {
            AddressView created = service.add(asha, command("Asha Rao"));

            AddressView updated = service.update(asha, created.id(),
                    new UpdateAddressCommand("Bhavna Rao", "9812345678", "99 Cacao Road", "Apt 4",
                            "Mysuru", "Karnataka", "570001", "Leave at the door"));

            assertThat(updated.recipientName()).isEqualTo("Bhavna Rao");
            assertThat(updated.city()).isEqualTo("Mysuru");
        }

        @Test
        void an_unknown_address_id_is_reported_as_not_found() {
            assertThatThrownBy(() -> service.update(asha, UUID.randomUUID(),
                    new UpdateAddressCommand("Asha Rao", "9876543210", "12 Cocoa Lane", null,
                            "Bengaluru", "Karnataka", "560001", null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("default handling")
    class Defaults {

        @Test
        void promotes_the_chosen_address() {
            AddressView first = service.add(asha, command("Asha Rao"));
            AddressView second = service.add(asha, command("Asha Rao"));

            service.makeDefault(asha, second.id());

            assertThat(service.get(asha, second.id()).isDefault()).isTrue();
            assertThat(service.get(asha, first.id()).isDefault()).isFalse();
        }

        @Test
        void there_is_never_more_than_one_default() {
            service.add(asha, command("Asha Rao"));
            AddressView second = service.add(asha, command("Asha Rao"));
            AddressView third = service.add(asha, command("Asha Rao"));

            service.makeDefault(asha, second.id());
            service.makeDefault(asha, third.id());

            assertThat(service.list(asha).stream().filter(AddressView::isDefault).count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("deleting")
    class Deleting {

        @Test
        void removes_the_address() {
            AddressView created = service.add(asha, command("Asha Rao"));

            service.delete(asha, created.id());

            assertThat(addresses.countByCustomerId(asha)).isZero();
        }

        @Test
        @DisplayName("deleting the default promotes another address")
        void deletingTheDefaultPromotesAnother() {
            AddressView first = service.add(asha, command("Asha Rao"));
            service.add(asha, command("Asha Rao"));
            service.makeDefault(asha, first.id());

            service.delete(asha, first.id());

            assertThat(service.list(asha))
                    .as("the customer must not be left without a default")
                    .anySatisfy(a -> assertThat(a.isDefault()).isTrue());
        }

        @Test
        void deleting_a_non_default_address_leaves_the_default_alone() {
            AddressView first = service.add(asha, command("Asha Rao"));
            AddressView second = service.add(asha, command("Asha Rao"));

            service.delete(asha, second.id());

            assertThat(service.get(asha, first.id()).isDefault()).isTrue();
        }

        @Test
        void an_unknown_address_id_is_reported_as_not_found() {
            assertThatThrownBy(() -> service.delete(asha, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Address not found");
        }
    }

    @Test
    void the_not_found_error_uses_the_address_code() {
        assertThatThrownBy(() -> service.get(asha, UUID.randomUUID()))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
    }
}
