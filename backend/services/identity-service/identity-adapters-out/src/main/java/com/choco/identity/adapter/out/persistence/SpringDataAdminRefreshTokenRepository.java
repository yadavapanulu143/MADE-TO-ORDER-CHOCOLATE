package com.choco.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAdminRefreshTokenRepository extends JpaRepository<AdminRefreshTokenJpaEntity, UUID> {

    Optional<AdminRefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /** Set-based so a rotation in flight cannot be missed by a stale read-modify-write loop. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AdminRefreshTokenJpaEntity t set t.revokedAt = :revokedAt "
            + "where t.adminId = :adminId and t.revokedAt is null")
    int revokeAll(@Param("adminId") UUID adminId, @Param("revokedAt") Instant revokedAt);
}
