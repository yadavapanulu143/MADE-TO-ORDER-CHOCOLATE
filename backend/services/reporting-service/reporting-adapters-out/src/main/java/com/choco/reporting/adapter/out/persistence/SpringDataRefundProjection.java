package com.choco.reporting.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRefundProjection extends JpaRepository<RefundProjectionJpaEntity, String> {

    java.util.List<RefundProjectionJpaEntity> findByRequestedAtGreaterThanEqualAndRequestedAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
