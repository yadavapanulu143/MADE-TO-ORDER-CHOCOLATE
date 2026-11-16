package com.choco.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /** Set-based so a rotation in flight cannot be missed by a stale read-modify-write loop. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :revokedAt "
            + "where t.customerId = :customerId and t.revokedAt is null")
    int revokeAll(@Param("customerId") UUID customerId, @Param("revokedAt") Instant revokedAt);
}
