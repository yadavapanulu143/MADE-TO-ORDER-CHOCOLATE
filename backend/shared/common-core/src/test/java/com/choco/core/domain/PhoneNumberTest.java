package com.choco.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PhoneNumber")
class PhoneNumberTest {

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        @DisplayName("accepts a plain 10-digit national number")
        void acceptsPlainNationalNumber() {
            assertThat(PhoneNumber.of("9876543210").value()).isEqualTo("9876543210");
        }

        /**
         * Regression: a valid national number can itself begin with the digits of the country code.
         * Stripping the prefix unconditionally removed "91" from 9123456780, leaving 8 digits, and the
         * number was rejected. Numbers beginning with 91 are issued and must work.
         */
        @Test
        @DisplayName("keeps a valid number that begins with the country-code digits")
        void keepsNumberBeginningWithCountryCodeDigits() {
            assertThat(PhoneNumber.of("9123456780").value()).isEqualTo("9123456780");
            assertThat(PhoneNumber.of("9198765432").value()).isEqualTo("9198765432");
        }

        @ParameterizedTest
        @ValueSource(strings = {"+919876543210", "919876543210", "09876543210"})
        @DisplayName("strips a country or trunk prefix from a longer input")
        void stripsPrefixFromLongerInput(String input) {
            assertThat(PhoneNumber.of(input).value()).isEqualTo("9876543210");
        }

        @Test
        @DisplayName("ignores spaces and hyphens")
        void ignoresSeparators() {
            assertThat(PhoneNumber.of("+91 98765-43210").value()).isEqualTo("9876543210");
        }

        @Test
        @DisplayName("renders with the country code for outbound use")
        void rendersWithCountryCode() {
            assertThat(PhoneNumber.of("9876543210").withCountryCode()).isEqualTo("+919876543210");
        }
    }

    @Nested
    @DisplayName("rejection")
    class Rejection {

        @ParameterizedTest
        @ValueSource(strings = {"1234567890", "5876543210", "98765", "98765432101", ""})
        @DisplayName("rejects numbers that are not valid Indian mobiles")
        void rejectsInvalidNumbers(String input) {
            assertThatThrownBy(() -> PhoneNumber.of(input))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> PhoneNumber.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @Test
        @DisplayName("tryParse returns empty instead of throwing")
        void tryParseReturnsEmpty() {
            assertThat(PhoneNumber.tryParse("12345")).isEmpty();
            assertThat(PhoneNumber.tryParse("9876543210")).isPresent();
        }
    }
}
