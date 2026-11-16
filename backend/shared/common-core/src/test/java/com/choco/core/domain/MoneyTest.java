package com.choco.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void parses_rupee_string_into_paise() {
            assertThat(Money.inrRupees("499.00").minorUnits()).isEqualTo(49_900L);
        }

        @Test
        void parses_fractional_rupees_exactly() {
            assertThat(Money.inrRupees("0.10").minorUnits()).isEqualTo(10L);
            assertThat(Money.inrRupees("1234.56").minorUnits()).isEqualTo(123_456L);
        }

        @Test
        void rounds_half_up_beyond_two_decimals() {
            assertThat(Money.inrRupees("10.005").minorUnits()).isEqualTo(1_001L);
        }

        @Test
        void rejects_non_inr_currency_mismatch_on_arithmetic() {
            Money inr = Money.inr(100);
            Money usd = Money.ofMinorUnits(100, Currency.getInstance("USD"));
            assertThatThrownBy(() -> inr.plus(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void adds_without_floating_point_drift() {
            Money total = Money.inr(10).plus(Money.inr(20)).plus(Money.inr(30));
            assertThat(total.minorUnits()).isEqualTo(60L);
        }

        @Test
        void multiplies_by_quantity() {
            assertThat(Money.inrRupees("349.50").multiply(3).minorUnits()).isEqualTo(104_850L);
        }

        @Test
        void subtracts() {
            assertThat(Money.inr(500).minus(Money.inr(120)).minorUnits()).isEqualTo(380L);
        }

        @Test
        void detects_overflow_instead_of_silently_wrapping() {
            Money huge = Money.inr(Long.MAX_VALUE);
            assertThatThrownBy(() -> huge.plus(Money.inr(1))).isInstanceOf(ArithmeticException.class);
        }
    }

    @Nested
    @DisplayName("equality and comparison")
    class Equality {

        @Test
        void is_value_based() {
            assertThat(Money.inr(100)).isEqualTo(Money.inr(100)).hasSameHashCodeAs(Money.inr(100));
        }

        @Test
        void compares_by_amount() {
            assertThat(Money.inr(100)).isLessThan(Money.inr(200));
            assertThat(Money.inr(200).isGreaterThan(Money.inr(100))).isTrue();
        }

        @Test
        void renders_major_units() {
            assertThat(Money.inr(49_900).toMajorUnits()).isEqualByComparingTo(new BigDecimal("499.00"));
            assertThat(Money.inr(49_900).toString()).isEqualTo("INR 499.00");
        }
    }
}
