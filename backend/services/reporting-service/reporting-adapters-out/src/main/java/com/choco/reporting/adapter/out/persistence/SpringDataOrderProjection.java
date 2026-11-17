package com.choco.reporting.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOrderProjection extends JpaRepository<OrderProjectionJpaEntity, String> {

    java.util.List<OrderProjectionJpaEntity> findByPlacedAtGreaterThanEqualAndPlacedAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
