package com.choco.reporting.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProductionProjection extends JpaRepository<ProductionProjectionJpaEntity, String> {

    java.util.List<ProductionProjectionJpaEntity> findByStartedAtGreaterThanEqualAndStartedAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
