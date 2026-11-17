package com.choco.fulfilment.application.command;

import com.choco.fulfilment.domain.inventory.StockLedgerReason;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A manual stock movement: a delivery received, a correction after a stock-take, or stock written off.
 *
 * <p>Consumption is never recorded through this command — it happens as a side effect of production —
 * so the seller cannot accidentally double-count what the kitchen already took.
 */
public record AdjustStockCommand(UUID ingredientId, BigDecimal amount, StockLedgerReason reason,
                                 String note) {
}
