package com.choco.identity.domain.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.error.BusinessRuleException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Address aggregate")
class AddressTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private final UUID customerId = UUID.randomUUID();

    private Address address(UUID owner) {
        return Address.create(UUID.randomUUID(), owner, "Asha Rao", PhoneNumber.of("9876543210"),
                "12 Cocoa Lane", "Near the park", "Bengaluru", "Karnataka", Pincode.of("560001"),
                "Ring the bell twice", false, NOW);
    }

    @Test
    void a_well_formed_address_is_accepted() {
        Address created = address(customerId);

        assertThat(created.recipientName()).isEqualTo("Asha Rao");
        assertThat(created.pincode().value()).isEqualTo("560001");
        assertThat(created.isDefault()).isFalse();
    }

    @Test
    void whitespace_in_text_fields_is_normalised() {
        Address created = Address.create(UUID.randomUUID(), customerId, "  Asha   Rao ",
                PhoneNumber.of("9876543210"), " 12 Cocoa  Lane ", null, " Bengaluru ", " Karnataka ",
                Pincode.of("560001"), null, false, NOW);

        assertThat(created.recipientName()).isEqualTo("Asha Rao");
        assertThat(created.line1()).isEqualTo("12 Cocoa Lane");
        assertThat(created.city()).isEqualTo("Bengaluru");
    }

    @Test
    void a_blank_required_field_is_rejected() {
        assertThatThrownBy(() -> Address.create(UUID.randomUUID(), customerId, "Asha Rao",
                PhoneNumber.of("9876543210"), "   ", null, "Bengaluru", "Karnataka",
                Pincode.of("560001"), null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Address line 1");
    }

    @Test
    void a_too_short_recipient_name_is_rejected() {
        assertThatThrownBy(() -> Address.create(UUID.randomUUID(), customerId, "A",
                PhoneNumber.of("9876543210"), "12 Cocoa Lane", null, "Bengaluru", "Karnataka",
                Pincode.of("560001"), null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    void an_address_must_belong_to_a_customer() {
        assertThatThrownBy(() -> Address.create(UUID.randomUUID(), null, "Asha Rao",
                PhoneNumber.of("9876543210"), "12 Cocoa Lane", null, "Bengaluru", "Karnataka",
                Pincode.of("560001"), null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer id");
    }

    @Test
    void optional_fields_may_be_absent() {
        Address created = Address.create(UUID.randomUUID(), customerId, "Asha Rao",
                PhoneNumber.of("9876543210"), "12 Cocoa Lane", null, "Bengaluru", "Karnataka",
                Pincode.of("560001"), null, false, NOW);

        assertThat(created.line2()).isNull();
        assertThat(created.deliveryInstructions()).isNull();
    }

    @Test
    void ownership_check_passes_for_the_owner() {
        Address created = address(customerId);

        created.assertOwnedBy(customerId);
    }

    @Test
    @DisplayName("ownership check rejects a different customer")
    void ownership_check_rejects_another_customer() {
        Address created = address(customerId);

        assertThatThrownBy(() -> created.assertOwnedBy(UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void ownership_check_rejects_a_null_caller() {
        Address created = address(customerId);

        assertThatThrownBy(() -> created.assertOwnedBy(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_replaces_the_editable_fields() {
        Address created = address(customerId);

        created.update("Bhavna Rao", PhoneNumber.of("9812345678"), "99 Cacao Road", "Apt 4",
                "Mysuru", "Karnataka", Pincode.of("570001"), "Leave at the door",
                NOW.plusSeconds(60));

        assertThat(created.recipientName()).isEqualTo("Bhavna Rao");
        assertThat(created.mobile().value()).isEqualTo("9812345678");
        assertThat(created.pincode().value()).isEqualTo("570001");
        assertThat(created.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void update_cannot_change_the_owning_customer() {
        Address created = address(customerId);
        UUID other = UUID.randomUUID();

        created.update("Bhavna Rao", PhoneNumber.of("9812345678"), "99 Cacao Road", null,
                "Mysuru", "Karnataka", Pincode.of("570001"), null, NOW.plusSeconds(60));

        assertThat(created.customerId()).isEqualTo(customerId);
        assertThatThrownBy(() -> created.assertOwnedBy(other))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void an_update_to_an_invalid_field_leaves_the_address_unchanged() {
        Address created = address(customerId);

        assertThatThrownBy(() -> created.update("Bhavna Rao", PhoneNumber.of("9812345678"),
                "   ", null, "Mysuru", "Karnataka", Pincode.of("570001"), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(created.line1()).isEqualTo("12 Cocoa Lane");
        assertThat(created.recipientName()).isEqualTo("Asha Rao");
    }

    @Test
    void the_default_flag_can_be_set_and_cleared() {
        Address created = address(customerId);

        created.markDefault();
        assertThat(created.isDefault()).isTrue();

        created.clearDefault();
        assertThat(created.isDefault()).isFalse();
    }
}
