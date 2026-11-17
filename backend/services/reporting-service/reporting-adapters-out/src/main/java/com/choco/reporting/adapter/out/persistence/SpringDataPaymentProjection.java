package com.choco.reporting.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentProjection extends JpaRepository<PaymentProjectionJpaEntity, String> {

    java.util.List<PaymentProjectionJpaEntity> findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
