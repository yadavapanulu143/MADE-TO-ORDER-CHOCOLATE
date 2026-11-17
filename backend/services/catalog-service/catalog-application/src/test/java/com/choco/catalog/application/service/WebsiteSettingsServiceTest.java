package com.choco.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.catalog.application.command.UpdateSettingsCommand;
import com.choco.catalog.application.fake.InMemoryWebsiteSettingsRepository;
import com.choco.catalog.application.port.AuditLogPort;
import com.choco.test.Fakes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests the website-settings use case. */
@DisplayName("WebsiteSettingsService")
class WebsiteSettingsServiceTest {

    private InMemoryWebsiteSettingsRepository repository;
    private List<String> auditActions;
    private WebsiteSettingsService service;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = new InMemoryWebsiteSettingsRepository();
        auditActions = new ArrayList<>();
        AuditLogPort auditLog = (actorType, actorId, action, targetType, targetId, outcome, detail, meta) ->
                auditActions.add(action);

        service = new WebsiteSettingsService(repository, auditLog, new Fakes.DirectTransactionRunner(),
                Fakes.FixedClock.startingAt("2026-06-01T09:00:00Z"));
    }

    private UpdateSettingsCommand command(String sellerName) {
        return new UpdateSettingsCommand(sellerName, "hello@choco.example", "9876543210",
                "12 Cocoa Lane, Bengaluru", "Privacy", "Terms", "Shipping", "Refund");
    }

    @Test
    void the_settings_row_is_created_on_first_read() {
        assertThat(service.get()).isNotNull();
        assertThat(repository.find()).isPresent();
    }

    @Test
    void updating_stores_the_values_and_audits_the_change() {
        var updated = service.update(admin, command("Cocoa House"));

        assertThat(updated.sellerName()).isEqualTo("Cocoa House");
        assertThat(updated.refundPolicy()).isEqualTo("Refund");
        assertThat(auditActions).containsExactly("SETTINGS_UPDATED");
    }

    @Test
    void the_settings_row_is_a_singleton_across_reads_and_writes() {
        service.update(admin, command("Cocoa House"));
        service.update(admin, command("Cocoa House Two"));

        assertThat(service.get().sellerName()).isEqualTo("Cocoa House Two");
        assertThat(repository.find()).isPresent();
    }
}
