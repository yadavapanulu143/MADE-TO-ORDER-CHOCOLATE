package com.choco.order.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInvoiceRepository extends JpaRepository<InvoiceJpaEntity, UUID> {

    Optional<InvoiceJpaEntity> findByOrderId(UUID orderId);

    /** One invoice, scoped to its owner. The customer predicate is part of the query, never a check. */
    Optional<InvoiceJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<InvoiceJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);

    boolean existsByOrderId(UUID orderId);
}
