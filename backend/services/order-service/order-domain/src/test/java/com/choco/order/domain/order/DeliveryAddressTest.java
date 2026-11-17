package com.choco.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Delivery address value object")
class DeliveryAddressTest {

    @Test
    @DisplayName("formats to a single line including the country code")
    void formatted() {
        DeliveryAddress address = new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"),
                "12 Cocoa Lane", "Flat 3", "Bengaluru", "Karnataka", Pincode.of("560001"), null);

        assertThat(address.formatted())
                .contains("Asha Rao", "12 Cocoa Lane", "Flat 3", "Bengaluru", "Karnataka", "560001",
                        "+919876543210");
    }

    @Test
    @DisplayName("omits line two from the formatted output when absent")
    void formattedWithoutLine2() {
        DeliveryAddress address = new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"),
                "12 Cocoa Lane", null, "Bengaluru", "Karnataka", Pincode.of("560001"), null);

        assertThat(address.formatted()).doesNotContain("null");
    }

    @Test
    @DisplayName("requires a recipient name of at least two characters")
    void requiresName() {
        assertThatThrownBy(() -> address("A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> address("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requires line 1, city, state, mobile and PIN code")
    void requiresMandatoryFields() {
        assertThatThrownBy(() -> new DeliveryAddress("Asha Rao", null, "L1", null, "City", "State",
                Pincode.of("560001"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), " ",
                null, "City", "State", Pincode.of("560001"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "L1",
                null, null, "State", Pincode.of("560001"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "L1",
                null, "City", null, Pincode.of("560001"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "L1",
                null, "City", "State", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DeliveryAddress address(String name) {
        return new DeliveryAddress(name, PhoneNumber.of("9876543210"), "L1", null, "City", "State",
                Pincode.of("560001"), null);
    }
}
