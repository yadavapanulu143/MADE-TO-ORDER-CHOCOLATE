package com.choco.reporting.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDeliveryProjection extends JpaRepository<DeliveryProjectionJpaEntity, String> {

    java.util.List<DeliveryProjectionJpaEntity> findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
