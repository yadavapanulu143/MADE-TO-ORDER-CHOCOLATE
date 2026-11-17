package com.choco.catalog.application.fake;

import com.choco.catalog.domain.port.WebsiteSettingsRepository;
import com.choco.catalog.domain.settings.WebsiteSettings;
import java.util.Optional;

/** In-memory {@link WebsiteSettingsRepository} holding the single settings row. */
public class InMemoryWebsiteSettingsRepository implements WebsiteSettingsRepository {

    private WebsiteSettings settings;

    @Override
    public WebsiteSettings save(WebsiteSettings toSave) {
        this.settings = toSave;
        return toSave;
    }

    @Override
    public Optional<WebsiteSettings> find() {
        return Optional.ofNullable(settings);
    }
}
