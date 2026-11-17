package com.choco.payment.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByOrderId(UUID orderId);

    Optional<PaymentJpaEntity> findByProviderOrderId(String providerOrderId);

    Optional<PaymentJpaEntity> findByProviderPaymentId(String providerPaymentId);

    Optional<PaymentJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<PaymentJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);

    long countByStatus(String status);

    @Query("""
            select p from PaymentJpaEntity p
            where p.status in :statuses
              and (:search is null
                   or cast(p.id as string) like :search escape '!'
                   or p.providerPaymentId like :search escape '!'
                   or p.providerOrderId like :search escape '!')
            """)
    Page<PaymentJpaEntity> search(@Param("statuses") Collection<String> statuses,
                                  @Param("search") String search, Pageable pageable);

    @Query("select p.version from PaymentJpaEntity p where p.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    @Query("select p.id from PaymentJpaEntity p where p.status = :status")
    List<UUID> findIdsByStatus(@Param("status") String status);
}
