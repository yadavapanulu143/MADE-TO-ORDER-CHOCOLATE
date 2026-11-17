package com.choco.fulfilment.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Stock level")
class StockLevelTest {

    @Test
    @DisplayName("a decimal amount is held exactly, to three places")
    void holdsThreeDecimalPlaces() {
        assertThat(StockLevel.of(new BigDecimal("0.125")).milliUnits()).isEqualTo(125);
        assertThat(StockLevel.of(new BigDecimal("1.5")).toUnits()).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("a tenth of a gram does not drift when subtracted a hundred times")
    void noFloatingPointDrift() {
        StockLevel level = StockLevel.of(new BigDecimal("10"));
        for (int i = 0; i < 100; i++) {
            level = level.minus(StockLevel.of(new BigDecimal("0.1")));
        }

        assertThat(level.toUnits()).isEqualByComparingTo("0");
        assertThat(level.isZero()).isTrue();
    }

    @Test
    @DisplayName("whole units and milli-units agree")
    void unitConversions() {
        assertThat(StockLevel.ofUnits(3).milliUnits()).isEqualTo(3000);
        assertThat(StockLevel.ofMilliUnits(3000).toUnits()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("arithmetic is exact")
    void arithmetic() {
        assertThat(StockLevel.ofUnits(2).plus(StockLevel.ofUnits(3)).toUnits()).isEqualByComparingTo("5");
        assertThat(StockLevel.ofUnits(5).minus(StockLevel.ofUnits(2)).toUnits()).isEqualByComparingTo("3");
        assertThat(StockLevel.ofUnits(2).multiply(4).toUnits()).isEqualByComparingTo("8");
        assertThat(StockLevel.ofUnits(2).negate().toUnits()).isEqualByComparingTo("-2");
    }

    @Test
    @DisplayName("sign predicates report the sign")
    void signPredicates() {
        assertThat(StockLevel.zero().isZero()).isTrue();
        assertThat(StockLevel.ofUnits(1).isPositive()).isTrue();
        assertThat(StockLevel.ofMilliUnits(-1).isNegative()).isTrue();
    }

    @Test
    @DisplayName("comparison orders by magnitude")
    void comparison() {
        assertThat(StockLevel.ofUnits(1).isLessThan(StockLevel.ofUnits(2))).isTrue();
        assertThat(StockLevel.ofUnits(2)).isGreaterThan(StockLevel.ofUnits(1));
        assertThat(StockLevel.ofUnits(1)).isEqualByComparingTo(StockLevel.ofMilliUnits(1000));
    }

    @Test
    @DisplayName("a null amount is refused")
    void nullRefused() {
        assertThatThrownBy(() -> StockLevel.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an amount finer than a thousandth is rounded, not silently truncated")
    void roundsToThreePlaces() {
        assertThat(StockLevel.of(new BigDecimal("1.0004")).milliUnits()).isEqualTo(1000);
        assertThat(StockLevel.of(new BigDecimal("1.0005")).milliUnits()).isEqualTo(1001);
    }
}
