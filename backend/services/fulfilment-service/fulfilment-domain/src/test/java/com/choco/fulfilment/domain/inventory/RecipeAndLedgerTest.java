package com.choco.fulfilment.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Recipe line and stock ledger entry")
class RecipeAndLedgerTest {

    private static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");

    @Test
    @DisplayName("a recipe line scales by the job quantity")
    void scalesByQuantity() {
        RecipeLine line = RecipeLine.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                StockLevel.ofUnits(25));

        assertThat(line.forQuantity(4)).isEqualTo(StockLevel.ofUnits(100));
    }

    @Test
    @DisplayName("a quantity below one is refused")
    void quantityMustBePositive() {
        RecipeLine line = RecipeLine.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                StockLevel.ofUnits(25));

        assertThatThrownBy(() -> line.forQuantity(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a non-positive per-unit amount is refused")
    void perUnitMustBePositive() {
        assertThatThrownBy(() -> RecipeLine.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                StockLevel.zero())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a recipe line must name a variant and an ingredient")
    void namesAreRequired() {
        assertThatThrownBy(() -> RecipeLine.of(UUID.randomUUID(), null, UUID.randomUUID(),
                StockLevel.ofUnits(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeLine.of(UUID.randomUUID(), UUID.randomUUID(), null,
                StockLevel.ofUnits(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a ledger entry records a signed movement and the resulting balance")
    void ledgerEntry() {
        StockLedgerEntry entry = StockLedgerEntry.of(UUID.randomUUID(), UUID.randomUUID(),
                StockLedgerReason.CONSUMED, StockLevel.ofUnits(-5), StockLevel.ofUnits(95),
                UUID.randomUUID(), "  job CHOCO-1  ", T0);

        assertThat(entry.delta()).isEqualTo(StockLevel.ofUnits(-5));
        assertThat(entry.balanceAfter()).isEqualTo(StockLevel.ofUnits(95));
        assertThat(entry.note()).isEqualTo("job CHOCO-1");
    }

    @Test
    @DisplayName("a ledger entry that moves nothing is refused")
    void zeroMovementRefused() {
        assertThatThrownBy(() -> StockLedgerEntry.of(UUID.randomUUID(), UUID.randomUUID(),
                StockLedgerReason.RECEIVED, StockLevel.zero(), StockLevel.zero(), null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a ledger entry must name an ingredient, a reason and a balance")
    void requiredFields() {
        UUID ingredient = UUID.randomUUID();
        assertThatThrownBy(() -> StockLedgerEntry.of(UUID.randomUUID(), null,
                StockLedgerReason.RECEIVED, StockLevel.ofUnits(1), StockLevel.ofUnits(1), null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLedgerEntry.of(UUID.randomUUID(), ingredient, null,
                StockLevel.ofUnits(1), StockLevel.ofUnits(1), null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLedgerEntry.of(UUID.randomUUID(), ingredient,
                StockLedgerReason.RECEIVED, StockLevel.ofUnits(1), null, null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLedgerEntry.of(UUID.randomUUID(), ingredient,
                StockLedgerReason.RECEIVED, StockLevel.ofUnits(1), StockLevel.ofUnits(1), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
