package com.choco.order.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataCartRepository extends JpaRepository<CartJpaEntity, UUID> {

    Optional<CartJpaEntity> findByCustomerId(UUID customerId);

    @Query("select c.version from CartJpaEntity c where c.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);
}
