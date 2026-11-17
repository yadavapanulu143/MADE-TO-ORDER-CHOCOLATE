package com.choco.fulfilment.domain.inventory;

import java.time.Instant;
import java.util.UUID;

/**
 * One movement of an ingredient's stock.
 *
 * <p>The ingredient holds a current level, but that level alone cannot answer "why is it where it is".
 * Every change appends an entry here — a delivery, a consumption against a job, a correction — so the
 * seller can reconcile a stock-take against what the system believes happened. The entry is signed:
 * a receipt is positive, a consumption is negative.
 */
public record StockLedgerEntry(
        UUID id,
        UUID ingredientId,
        StockLedgerReason reason,
        StockLevel delta,
        StockLevel balanceAfter,
        UUID productionJobId,
        String note,
        Instant occurredAt) {

    public StockLedgerEntry {
        if (id == null) {
            throw new IllegalArgumentException("Ledger entry id is required");
        }
        if (ingredientId == null) {
            throw new IllegalArgumentException("Ledger entry must name an ingredient");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Ledger entry must state a reason");
        }
        if (delta == null || delta.isZero()) {
            throw new IllegalArgumentException("Ledger entry must move the level");
        }
        if (balanceAfter == null) {
            throw new IllegalArgumentException("Ledger entry must record the resulting balance");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Ledger entry must be timestamped");
        }
        note = note == null ? null : note.trim();
    }

    public static StockLedgerEntry of(UUID id, UUID ingredientId, StockLedgerReason reason,
                                      StockLevel delta, StockLevel balanceAfter, UUID productionJobId,
                                      String note, Instant at) {
        return new StockLedgerEntry(id, ingredientId, reason, delta, balanceAfter, productionJobId, note, at);
    }
}
