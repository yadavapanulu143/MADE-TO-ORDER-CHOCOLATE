package com.choco.fulfilment.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.inventory.StockLedgerEntry;
import com.choco.fulfilment.domain.port.StockLedgerRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** In-memory, append-only {@link StockLedgerRepository}. Newest entries come back first. */
public class InMemoryStockLedgerRepository implements StockLedgerRepository {

    private final List<StockLedgerEntry> entries = new ArrayList<>();

    @Override
    public StockLedgerEntry append(StockLedgerEntry entry) {
        entries.add(entry);
        return entry;
    }

    @Override
    public PageResult<StockLedgerEntry> findByIngredient(UUID ingredientId, PageQuery page) {
        List<StockLedgerEntry> matches = entries.stream()
                .filter(e -> e.ingredientId().equals(ingredientId))
                .sorted(Comparator.comparing(StockLedgerEntry::occurredAt).reversed())
                .toList();
        return InMemoryPage.of(matches, page);
    }

    @Override
    public long count() {
        return entries.size();
    }

    /** Test helper: every movement recorded, for assertions. */
    public List<StockLedgerEntry> all() {
        return List.copyOf(entries);
    }
}
