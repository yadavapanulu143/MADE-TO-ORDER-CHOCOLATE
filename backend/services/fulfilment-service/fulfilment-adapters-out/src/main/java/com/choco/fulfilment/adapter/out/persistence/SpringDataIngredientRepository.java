package com.choco.fulfilment.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataIngredientRepository extends JpaRepository<IngredientJpaEntity, UUID> {

    boolean existsByNameIgnoreCase(String name);

    @Query("select i.version from IngredientJpaEntity i where i.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    @Query("""
            select i from IngredientJpaEntity i
            where (:includeInactive = true or i.active = true)
              and lower(i.name) like :pattern escape '!'
            """)
    Page<IngredientJpaEntity> search(@Param("includeInactive") boolean includeInactive,
                                     @Param("pattern") String pattern, Pageable pageable);

    /** Active ingredients at or below their threshold. A zero threshold means "never alert". */
    @Query("""
            select i from IngredientJpaEntity i
            where i.active = true
              and i.minimumMilliUnits > 0
              and i.onHandMilliUnits <= i.minimumMilliUnits
            order by i.name asc
            """)
    List<IngredientJpaEntity> findLowStock();
}
