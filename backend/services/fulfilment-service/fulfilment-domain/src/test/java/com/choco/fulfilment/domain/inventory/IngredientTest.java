package com.choco.fulfilment.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.fulfilment.domain.event.LowStockDetected;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ingredient")
class IngredientTest {

    private static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");
    private static final Instant LATER = T0.plusSeconds(600);

    private Ingredient ingredient(StockLevel opening, StockLevel minimum) {
        return Ingredient.create(UUID.randomUUID(), "Cocoa Butter", UnitOfMeasure.GRAM, opening,
                minimum, T0);
    }

    @Test
    @DisplayName("an ingredient starts active with the opening level and a zero minimum means no alert")
    void creation() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(1000), StockLevel.zero());

        assertThat(ingredient.active()).isTrue();
        assertThat(ingredient.onHand()).isEqualTo(StockLevel.ofUnits(1000));
        assertThat(ingredient.isBelowMinimum()).isFalse();
    }

    @Test
    @DisplayName("receiving stock raises the level")
    void receive() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(100), StockLevel.zero());
        ingredient.receive(StockLevel.ofUnits(50), "supplier delivery", LATER);

        assertThat(ingredient.onHand()).isEqualTo(StockLevel.ofUnits(150));
        assertThat(ingredient.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("a non-positive receipt is refused")
    void receiveRequiresPositive() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(100), StockLevel.zero());

        assertThatThrownBy(() -> ingredient.receive(StockLevel.zero(), null, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ingredient.receive(StockLevel.ofUnits(-1), null, LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an adjustment moves the level by a signed amount")
    void adjust() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(100), StockLevel.zero());
        ingredient.adjust(StockLevel.ofMilliUnits(-25000), "stock-take", LATER);

        assertThat(ingredient.onHand()).isEqualTo(StockLevel.ofUnits(75));
    }

    @Test
    @DisplayName("an adjustment below zero is refused: a negative level is not a real state")
    void adjustCannotGoNegative() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(10), StockLevel.zero());

        assertThatThrownBy(() -> ingredient.adjust(StockLevel.ofUnits(-50), "mistake", LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a zero adjustment is refused")
    void zeroAdjustmentRefused() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(10), StockLevel.zero());

        assertThatThrownBy(() -> ingredient.adjust(StockLevel.zero(), null, LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("consuming lowers the level and raises a low-stock event when it drops to the minimum")
    void consumeRaisesLowStock() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(100), StockLevel.ofUnits(30));
        ingredient.consume(StockLevel.ofUnits(70), LATER);

        assertThat(ingredient.onHand()).isEqualTo(StockLevel.ofUnits(30));
        assertThat(ingredient.isBelowMinimum()).isTrue();
        assertThat(ingredient.peekDomainEvents()).hasAtLeastOneElementOfType(LowStockDetected.class);
    }

    @Test
    @DisplayName("consuming more than is available is refused with the shortfall")
    void cannotConsumeMoreThanAvailable() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(10), StockLevel.zero());

        assertThatThrownBy(() -> ingredient.consume(StockLevel.ofUnits(20), LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Not enough");
    }

    @Test
    @DisplayName("a zero minimum never raises a low-stock alert, however low the level")
    void zeroMinimumNeverAlerts() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(1), StockLevel.zero());
        ingredient.consume(StockLevel.ofUnits(1), LATER);

        assertThat(ingredient.isBelowMinimum()).isFalse();
        assertThat(ingredient.peekDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("a negative minimum is refused")
    void negativeMinimumRefused() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(10), StockLevel.zero());

        assertThatThrownBy(() -> ingredient.setMinimumLevel(StockLevel.ofUnits(-1), LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retiring an ingredient keeps its history and stops it being active")
    void deactivate() {
        Ingredient ingredient = ingredient(StockLevel.ofUnits(10), StockLevel.zero());
        ingredient.deactivate(LATER);

        assertThat(ingredient.active()).isFalse();
        assertThat(ingredient.onHand()).isEqualTo(StockLevel.ofUnits(10));
    }

    @Test
    @DisplayName("a missing name is refused")
    void nameRequired() {
        assertThatThrownBy(() -> Ingredient.create(UUID.randomUUID(), "  ", UnitOfMeasure.GRAM,
                StockLevel.zero(), StockLevel.zero(), T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a rehydrated ingredient does not re-run creation rules")
    void rehydrate() {
        Ingredient ingredient = Ingredient.rehydrate(UUID.randomUUID(), "Sugar", UnitOfMeasure.KILOGRAM,
                StockLevel.ofUnits(5), StockLevel.ofUnits(1), false, T0, LATER);

        assertThat(ingredient.unit()).isEqualTo(UnitOfMeasure.KILOGRAM);
        assertThat(ingredient.active()).isFalse();
        assertThat(ingredient.peekDomainEvents()).isEmpty();
    }
}
