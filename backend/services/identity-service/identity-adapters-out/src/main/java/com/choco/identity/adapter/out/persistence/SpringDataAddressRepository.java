package com.choco.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAddressRepository extends JpaRepository<AddressJpaEntity, UUID> {

    /** Default first, then most recently updated, so the customer's usual address leads the list. */
    List<AddressJpaEntity> findByCustomerIdOrderByIsDefaultDescUpdatedAtDesc(UUID customerId);

    Optional<AddressJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<AddressJpaEntity> findFirstByCustomerIdAndIsDefaultTrue(UUID customerId);

    long countByCustomerId(UUID customerId);

    /**
     * Clears the default flag across a customer's addresses in one statement.
     *
     * <p>A bulk update rather than a read-modify-write loop, so promoting a new default cannot race
     * with a concurrent request and leave two rows flagged.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AddressJpaEntity a set a.isDefault = false where a.customerId = :customerId and a.isDefault = true")
    void clearDefaultForCustomer(@Param("customerId") UUID customerId);
}
