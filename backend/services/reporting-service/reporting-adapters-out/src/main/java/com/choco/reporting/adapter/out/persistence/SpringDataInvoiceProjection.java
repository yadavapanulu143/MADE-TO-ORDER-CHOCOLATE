package com.choco.reporting.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInvoiceProjection extends JpaRepository<InvoiceProjectionJpaEntity, String> {

    java.util.List<InvoiceProjectionJpaEntity> findByIssuedAtGreaterThanEqualAndIssuedAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
