package com.choco.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Customer snapshot value object")
class CustomerSnapshotTest {

    @Test
    @DisplayName("collapses whitespace in the name")
    void normalisesName() {
        CustomerSnapshot snapshot = new CustomerSnapshot("  Asha   Rao ", EmailAddress.of("a@b.com"),
                PhoneNumber.of("9876543210"));
        assertThat(snapshot.fullName()).isEqualTo("Asha Rao");
    }

    @Test
    @DisplayName("requires a name, email and mobile")
    void requiresFields() {
        assertThatThrownBy(() -> new CustomerSnapshot("  ", EmailAddress.of("a@b.com"),
                PhoneNumber.of("9876543210"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerSnapshot("Asha", null, PhoneNumber.of("9876543210")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerSnapshot("Asha", EmailAddress.of("a@b.com"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
