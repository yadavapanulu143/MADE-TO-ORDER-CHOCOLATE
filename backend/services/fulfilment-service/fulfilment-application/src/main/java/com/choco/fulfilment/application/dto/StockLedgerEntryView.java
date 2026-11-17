package com.choco.fulfilment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One movement of an ingredient's stock, newest first in the ledger. */
public record StockLedgerEntryView(
        UUID id,
        UUID ingredientId,
        String reason,
        BigDecimal delta,
        long deltaMilliUnits,
        BigDecimal balanceAfter,
        long balanceAfterMilliUnits,
        UUID productionJobId,
        String note,
        Instant occurredAt) {
}
