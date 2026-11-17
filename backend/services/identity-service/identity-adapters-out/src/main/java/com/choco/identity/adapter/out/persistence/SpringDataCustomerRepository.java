package com.choco.identity.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataCustomerRepository extends JpaRepository<CustomerJpaEntity, UUID> {

    Optional<CustomerJpaEntity> findByEmail(String email);

    Optional<CustomerJpaEntity> findByMobile(String mobile);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);

    /**
     * The seller's customer list: newest first, with an optional case-insensitive search over the name,
     * email or mobile.
     *
     * <p>The search term is passed as a pre-built LIKE pattern rather than a nullable parameter. A
     * nullable parameter compared with {@code is null} lets PostgreSQL infer its type from the null
     * test — as {@code bytea} — and then {@code lower(bytea)} does not exist, so every search fails.
     * Building the pattern in the adapter keeps the parameter non-null and the query simple; the
     * {@code !} escape character is declared here so a term containing {@code %} or {@code _} matches
     * literally.
     */
    @Query("""
            select c from CustomerJpaEntity c
            where lower(c.fullName) like :pattern escape '!'
               or lower(c.email) like :pattern escape '!'
               or c.mobile like :pattern escape '!'
            """)
    Page<CustomerJpaEntity> search(@Param("pattern") String pattern, Pageable pageable);
}
