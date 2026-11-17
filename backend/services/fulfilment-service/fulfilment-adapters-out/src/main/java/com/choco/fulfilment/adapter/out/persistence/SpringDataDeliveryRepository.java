package com.choco.fulfilment.adapter.out.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDeliveryRepository extends JpaRepository<DeliveryJpaEntity, UUID> {

    Optional<DeliveryJpaEntity> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    long countByStatus(String status);

    @Query("select d.version from DeliveryJpaEntity d where d.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    Page<DeliveryJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("""
            select d from DeliveryJpaEntity d
            where d.status in :statuses
              and (lower(d.orderNumber) like :pattern escape '!'
                   or lower(d.addressRecipientName) like :pattern escape '!')
            """)
    Page<DeliveryJpaEntity> search(@Param("statuses") Collection<String> statuses,
                                   @Param("pattern") String pattern, Pageable pageable);
}
