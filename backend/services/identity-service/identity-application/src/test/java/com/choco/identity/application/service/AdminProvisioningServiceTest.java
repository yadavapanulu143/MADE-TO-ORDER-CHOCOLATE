package com.choco.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.ValidationException;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.PasswordPolicy;
import com.choco.identity.application.fake.InMemoryAdminRepository;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.domain.admin.AdminAccount;
import com.choco.identity.domain.admin.AdminRole;
import com.choco.test.Fakes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the one-time administrator bootstrap.
 *
 * <p>The behaviours that matter are that a weak configured password is refused rather than seeded,
 * that an existing administrator is never overwritten, and that no credentials means no account.
 */
@DisplayName("AdminProvisioningService")
class AdminProvisioningServiceTest {

    private InMemoryAdminRepository admins;
    private AdminProvisioningService service;
    private final List<String> auditActions = new ArrayList<>();

    private static final class TestHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return storedHash.equals(hash(rawPassword));
        }

        @Override
        public boolean needsRehash(String storedHash) {
            return false;
        }
    }

    private static final PasswordPolicy POLICY = raw ->
            raw != null && raw.length() >= 12 ? List.of() : List.of("Password must be at least 12 characters");

    @BeforeEach
    void setUp() {
        admins = new InMemoryAdminRepository();
        auditActions.clear();
        AuditLogPort audit = (actorType, actorId, action, targetType, targetId, outcome, detail, correlationId) ->
                auditActions.add(action);
        service = new AdminProvisioningService(admins, new TestHasher(), POLICY, audit,
                new Fakes.SequentialIdGenerator(), Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z"));
    }

    @Test
    void creates_a_super_admin_from_supplied_credentials() {
        var created = service.ensureBootstrapAdmin("owner@choco.example", "a-long-enough-passphrase", "Asha");

        assertThat(created).contains("owner@choco.example");
        assertThat(admins.count()).isEqualTo(1);
        AdminAccount saved = admins.findByEmail(com.choco.core.domain.EmailAddress.of("owner@choco.example"))
                .orElseThrow();
        assertThat(saved.role()).isEqualTo(AdminRole.SUPER_ADMIN);
        // The stored value is a hash, never the raw password.
        assertThat(saved.passwordHash()).isEqualTo("hashed:a-long-enough-passphrase");
        assertThat(auditActions).contains("ADMIN_BOOTSTRAP_CREATED");
    }

    @Test
    void a_weak_password_is_refused_rather_than_seeded() {
        assertThatThrownBy(() -> service.ensureBootstrapAdmin("owner@choco.example", "short", "Asha"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.WEAK_PASSWORD);

        assertThat(admins.count())
                .as("no account is created when the configured password is rejected")
                .isZero();
    }

    @Test
    void no_credentials_means_no_account() {
        assertThat(service.ensureBootstrapAdmin("", "", "Asha")).isEmpty();
        assertThat(service.ensureBootstrapAdmin(null, null, "Asha")).isEmpty();
        assertThat(service.ensureBootstrapAdmin("owner@choco.example", "", "Asha")).isEmpty();
        assertThat(admins.count()).isZero();
    }

    @Test
    void an_existing_administrator_is_never_overwritten() {
        service.ensureBootstrapAdmin("owner@choco.example", "a-long-enough-passphrase", "Asha");
        AdminAccount existing = admins.findByEmail(com.choco.core.domain.EmailAddress.of("owner@choco.example"))
                .orElseThrow();
        String originalHash = existing.passwordHash();

        // A restart with a different configured password must not reset the live account.
        var second = service.ensureBootstrapAdmin("owner@choco.example", "a-totally-different-pass", "Asha");

        assertThat(second).isEmpty();
        assertThat(admins.count()).isEqualTo(1);
        assertThat(admins.findById(existing.id()).orElseThrow().passwordHash()).isEqualTo(originalHash);
        assertThat(auditActions).contains("ADMIN_BOOTSTRAP_SKIPPED");
    }

    @Test
    void the_audit_record_never_contains_the_password() {
        List<String> details = new ArrayList<>();
        AuditLogPort capturing = (actorType, actorId, action, targetType, targetId, outcome, detail, correlationId) -> {
            if (detail != null) {
                details.add(detail);
            }
        };
        AdminProvisioningService capturingService = new AdminProvisioningService(admins, new TestHasher(),
                POLICY, capturing, new Fakes.SequentialIdGenerator(),
                Fakes.FixedClock.startingAt("2026-03-01T09:00:00Z"));

        capturingService.ensureBootstrapAdmin("owner@choco.example", "a-long-enough-passphrase", "Asha");

        assertThat(details).noneMatch(d -> d.contains("a-long-enough-passphrase"));
    }
}
