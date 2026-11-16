package com.choco.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ValueObjectTest {

    @Test
    void pincode_accepts_valid_indian_code() {
        assertThat(Pincode.of("560001").value()).isEqualTo("560001");
    }

    @Test
    void pincode_rejects_leading_zero_and_wrong_length() {
        assertThatThrownBy(() -> Pincode.of("060001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pincode.of("56000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pincode.of("5600012")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void email_is_normalised_to_lower_case_and_trimmed() {
        assertThat(EmailAddress.of("  Buyer@Example.COM ").value()).isEqualTo("buyer@example.com");
    }

    @Test
    void email_rejects_malformed_input() {
        assertThatThrownBy(() -> EmailAddress.of("not-an-email")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailAddress.of("a@b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phone_number_normalises_country_code_variants() {
        assertThat(PhoneNumber.of("+91 98765 43210").value()).isEqualTo("9876543210");
        assertThat(PhoneNumber.of("09876543210").value()).isEqualTo("9876543210");
        assertThat(PhoneNumber.of("919876543210").value()).isEqualTo("9876543210");
        assertThat(PhoneNumber.of("9876543210").withCountryCode()).isEqualTo("+919876543210");
    }

    @Test
    void phone_number_rejects_landline_and_short_numbers() {
        assertThatThrownBy(() -> PhoneNumber.of("1234567890")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumber.of("987654321")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quantity_must_be_positive_and_bounded() {
        assertThat(Quantity.of(1).value()).isEqualTo(1);
        assertThat(Quantity.of(3).plus(Quantity.of(2)).value()).isEqualTo(5);
        assertThatThrownBy(() -> Quantity.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantity.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantity.of(Quantity.MAX_PER_ITEM + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
