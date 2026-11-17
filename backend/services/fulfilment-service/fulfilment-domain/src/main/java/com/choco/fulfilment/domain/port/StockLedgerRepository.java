package com.choco.fulfilment.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.inventory.StockLedgerEntry;
import java.util.UUID;

/**
 * Persistence port for the stock ledger.
 *
 * <p>The ledger is append-only: entries are written and read, never updated or deleted. It exists to
 * answer "why is the level where it is", so a mutable entry would defeat its purpose.
 */
public interface StockLedgerRepository {

    StockLedgerEntry append(StockLedgerEntry entry);

    /** A page of movements for one ingredient, newest first. */
    PageResult<StockLedgerEntry> findByIngredient(UUID ingredientId, PageQuery page);

    long count();
}
