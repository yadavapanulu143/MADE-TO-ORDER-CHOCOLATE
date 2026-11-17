package com.choco.catalog.application.service;

import com.choco.catalog.application.command.UpdateSettingsCommand;
import com.choco.catalog.application.dto.SettingsView;
import com.choco.catalog.application.port.AuditLogPort;
import com.choco.catalog.domain.port.WebsiteSettingsRepository;
import com.choco.catalog.domain.settings.WebsiteSettings;
import com.choco.core.port.Clock;
import com.choco.core.port.TransactionRunner;
import java.time.Instant;
import java.util.UUID;

/**
 * Manages the single website-settings row.
 *
 * <p>The row is created lazily, on first read, by a well-known id. Seeding it in a migration would put
 * an application-generated id in the schema, and creating it on startup would race when two instances
 * boot together. A fixed id used by the read and the write means both converge on one row, and the
 * database's primary key makes the race harmless: the second insert loses and the read then finds the
 * row that won.
 */
public class WebsiteSettingsService {

    /**
     * The one settings row's identifier.
     *
     * <p>All-zeroes so it is unmistakably a singleton rather than a real entity id, and so a stray row
     * with a random id is obviously not the settings.
     */
    static final UUID SETTINGS_ID = new UUID(0L, 0L);

    private final WebsiteSettingsRepository repository;
    private final AuditLogPort auditLog;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public WebsiteSettingsService(WebsiteSettingsRepository repository,
                                  AuditLogPort auditLog,
                                  TransactionRunner transactionRunner,
                                  Clock clock) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /** The settings, creating an empty row on first access so a fresh install has something to edit. */
    public SettingsView get() {
        return ProductViewMapper.toView(loadOrCreate());
    }

    public SettingsView update(UUID actorId, UpdateSettingsCommand command) {
        Instant now = clock.now();
        WebsiteSettings settings = loadOrCreate();

        transactionRunner.execute(() -> {
            settings.update(command.sellerName(), command.contactEmail(), command.contactPhone(),
                    command.contactAddress(), command.privacyPolicy(), command.termsAndConditions(),
                    command.shippingPolicy(), command.refundPolicy(), now);
            repository.save(settings);
        });

        auditLog.record("ADMIN", actorId.toString(), "SETTINGS_UPDATED", "SETTINGS",
                SETTINGS_ID.toString(), "SUCCESS", null, null);
        return ProductViewMapper.toView(settings);
    }

    private WebsiteSettings loadOrCreate() {
        return repository.find().orElseGet(() -> {
            WebsiteSettings created = WebsiteSettings.empty(SETTINGS_ID, clock.now());
            return transactionRunner.execute(() -> repository.save(created));
        });
    }
}
