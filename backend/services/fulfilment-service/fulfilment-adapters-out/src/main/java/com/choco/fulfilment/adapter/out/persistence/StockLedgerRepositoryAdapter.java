package com.choco.fulfilment.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.inventory.StockLedgerEntry;
import com.choco.fulfilment.domain.port.StockLedgerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link StockLedgerRepository}.
 *
 * <p>Append-only: there is no update or delete here, because a ledger that can be rewritten answers
 * nothing. Entries are ordered newest first, which is the order a seller reads them in.
 */
@Repository
class StockLedgerRepositoryAdapter implements StockLedgerRepository {

    private final SpringDataStockLedgerRepository delegate;

    StockLedgerRepositoryAdapter(SpringDataStockLedgerRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public StockLedgerEntry append(StockLedgerEntry entry) {
        delegate.save(FulfilmentPersistenceMapper.toEntity(entry));
        return entry;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockLedgerEntry> findByIngredient(UUID ingredientId, PageQuery page) {
        Page<StockLedgerEntryJpaEntity> result = delegate.findByIngredientIdOrderByOccurredAtDesc(
                ingredientId, PageRequest.of(page.page(), page.size()));
        List<StockLedgerEntry> entries = result.getContent().stream()
                .map(FulfilmentPersistenceMapper::toDomain).toList();
        return PageResult.of(entries, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }
}
