package com.choco.catalog.adapter.out.persistence;

import com.choco.catalog.domain.port.WebsiteSettingsRepository;
import com.choco.catalog.domain.settings.WebsiteSettings;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JPA implementation of {@link WebsiteSettingsRepository}. */
@Repository
class WebsiteSettingsRepositoryAdapter implements WebsiteSettingsRepository {

    private final SpringDataWebsiteSettingsRepository delegate;

    WebsiteSettingsRepositoryAdapter(SpringDataWebsiteSettingsRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public WebsiteSettings save(WebsiteSettings settings) {
        delegate.save(CatalogPersistenceMapper.toEntity(settings));
        return settings;
    }

    @Override
    public Optional<WebsiteSettings> find() {
        return delegate.findFirstByOrderByIdAsc().map(CatalogPersistenceMapper::toDomain);
    }
}
