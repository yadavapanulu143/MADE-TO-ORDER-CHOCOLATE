package com.choco.reporting.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCustomerProjection extends JpaRepository<CustomerProjectionJpaEntity, String> {

    java.util.List<CustomerProjectionJpaEntity> findByRegisteredAtGreaterThanEqualAndRegisteredAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
