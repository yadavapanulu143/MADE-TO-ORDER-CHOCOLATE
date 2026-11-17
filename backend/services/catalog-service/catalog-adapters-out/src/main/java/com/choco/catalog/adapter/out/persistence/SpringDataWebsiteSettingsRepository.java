package com.choco.catalog.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWebsiteSettingsRepository extends JpaRepository<WebsiteSettingsJpaEntity, UUID> {

    /** The settings row is a singleton; the first row found is the one. */
    Optional<WebsiteSettingsJpaEntity> findFirstByOrderByIdAsc();
}
