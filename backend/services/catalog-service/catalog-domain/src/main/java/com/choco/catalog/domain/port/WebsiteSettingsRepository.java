package com.choco.catalog.domain.port;

import com.choco.catalog.domain.settings.WebsiteSettings;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the single website-settings row.
 *
 * <p>There is exactly one set of settings for the seller, so the read is a "find the one row" rather
 * than a lookup by id. The id is fixed by the application at first write, which keeps the table a
 * single-row table without a special case in the schema.
 */
public interface WebsiteSettingsRepository {

    WebsiteSettings save(WebsiteSettings settings);

    Optional<WebsiteSettings> find();
}
