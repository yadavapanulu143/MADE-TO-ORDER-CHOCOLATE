package com.choco.catalog.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataProductRepository extends JpaRepository<ProductJpaEntity, UUID> {

    Optional<ProductJpaEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByStatus(String status);

    Optional<ProductJpaEntity> findBySlugAndStatus(String slug, String status);

    /**
     * Reads only the optimistic-lock version of a row.
     *
     * <p>Used by the save path, which builds a fresh entity from the aggregate and has to carry the
     * current version across so Hibernate compares against the row as it was loaded. A scalar projection
     * rather than loading the whole entity: the version is all that is needed, and loading the product
     * with its children would be wasted work on every write.
     */
    @Query("select p.version from ProductJpaEntity p where p.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    /**
     * The dashboard listing: any of the given statuses, with an optional case-insensitive search.
     *
     * <p>The search term reaches the database as a bound {@code LIKE} pattern, never concatenated
     * into the query text, so a term containing SQL metacharacters is treated as data. The pattern is
     * always supplied — {@code "%"} when the caller searches for nothing — rather than checked for
     * {@code null} in the query. A {@code :search is null} test made PostgreSQL infer the parameter's
     * type from an {@code IS NULL} comparison and bind it as {@code bytea}, which then failed on
     * {@code lower(...)}. Supplying a real pattern removes that ambiguity and lets one query serve both
     * the filtered and unfiltered cases.
     */
    @Query("""
            select p from ProductJpaEntity p
            where p.status in :statuses
              and lower(p.name) like :pattern escape '!'
            """)
    Page<ProductJpaEntity> searchByStatuses(@Param("statuses") Collection<String> statuses,
                                            @Param("pattern") String pattern,
                                            Pageable pageable);

    /** The storefront listing: published products only, with an optional case-insensitive search. */
    @Query("""
            select p from ProductJpaEntity p
            where p.status = 'PUBLISHED'
              and lower(p.name) like :pattern escape '!'
            """)
    Page<ProductJpaEntity> searchPublished(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Resolves the products that own any of the given variants, published ones only.
     *
     * <p>Used by the order service at checkout to price and validate a cart. The status filter is part
     * of the query rather than a check the caller must remember, so a draft product's variant can
     * never be priced. A variant id that matches nothing simply yields no product, which the caller
     * reads as "unknown or unavailable".
     */
    @Query("""
            select distinct p from ProductJpaEntity p
            join p.variants v
            where v.id in :variantIds
              and p.status = 'PUBLISHED'
            """)
    List<ProductJpaEntity> findPublishedByVariantIds(@Param("variantIds") Collection<UUID> variantIds);
}
