package com.choco.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAdminRepository extends JpaRepository<AdminJpaEntity, UUID> {

    Optional<AdminJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
