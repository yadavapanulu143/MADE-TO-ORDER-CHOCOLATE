package com.choco.fulfilment.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataServiceableAreaRepository extends JpaRepository<ServiceableAreaJpaEntity, UUID> {

    boolean existsByPincodePrefix(String pincodePrefix);

    @Query("select a.version from ServiceableAreaJpaEntity a where a.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    /**
     * The most specific active area covering the PIN code: longest prefix first.
     *
     * <p>{@code startsWith} is expressed as a LIKE with the PIN as the pattern prefix, so an exact
     * six-digit entry outranks the city-wide entry that also matches. Ordering by length descending and
     * taking the first row is the longest-prefix rule applied in the database.
     */
    @Query("""
            select a from ServiceableAreaJpaEntity a
            where a.active = true
              and :pincode like concat(a.pincodePrefix, '%')
            order by length(a.pincodePrefix) desc
            """)
    java.util.List<ServiceableAreaJpaEntity> findBestMatch(@Param("pincode") String pincode,
                                                           Pageable pageable);

    @Query("""
            select a from ServiceableAreaJpaEntity a
            where (:includeInactive = true or a.active = true)
              and lower(a.areaName) like :pattern escape '!'
            """)
    Page<ServiceableAreaJpaEntity> search(@Param("includeInactive") boolean includeInactive,
                                          @Param("pattern") String pattern, Pageable pageable);
}
