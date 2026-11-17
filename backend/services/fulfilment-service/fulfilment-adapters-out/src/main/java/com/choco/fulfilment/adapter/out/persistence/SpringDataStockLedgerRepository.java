package com.choco.fulfilment.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataStockLedgerRepository extends JpaRepository<StockLedgerEntryJpaEntity, UUID> {

    Page<StockLedgerEntryJpaEntity> findByIngredientIdOrderByOccurredAtDesc(UUID ingredientId,
                                                                           Pageable pageable);
}
