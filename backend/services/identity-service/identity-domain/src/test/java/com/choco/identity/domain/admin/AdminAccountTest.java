package com.choco.identity.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AdminAccount")
class AdminAccountTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private static AdminAccount account() {
        return AdminAccount.provision(UUID.randomUUID(), "Asha Menon",
                EmailAddress.of("owner@choco.example"), "hashed:secret", AdminRole.SUPER_ADMIN, NOW);
    }

    @Test
    void a_provisioned_account_starts_active_and_unlocked() {
        AdminAccount admin = account();

        assertThat(admin.isActive()).isTrue();
        assertThat(admin.failedLoginAttempts()).isZero();
        assertThat(admin.lockedUntil()).isNull();
        assertThat(admin.role()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(admin.isLocked(NOW)).isFalse();
    }

    @Test
    void provisioning_requires_a_name_and_a_password_hash() {
        assertThatThrownBy(() -> AdminAccount.provision(UUID.randomUUID(), "  ",
                EmailAddress.of("a@b.com"), "hash", AdminRole.ADMIN, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdminAccount.provision(UUID.randomUUID(), "Asha",
                EmailAddress.of("a@b.com"), "", AdminRole.ADMIN, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("lockout")
    class Lockout {

        @Test
        void locks_after_the_maximum_number_of_failed_attempts() {
            AdminAccount admin = account();
            for (int i = 0; i < AdminAccount.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
                admin.recordFailedLogin(NOW);
            }

            assertThat(admin.isLocked(NOW)).isTrue();
            assertThatThrownBy(() -> admin.assertUsable(NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED);
        }

        @Test
        void a_successful_login_clears_the_counter_and_the_lock() {
            AdminAccount admin = account();
            for (int i = 0; i < AdminAccount.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
                admin.recordFailedLogin(NOW);
            }

            admin.recordSuccessfulLogin(NOW);

            assertThat(admin.failedLoginAttempts()).isZero();
            assertThat(admin.isLocked(NOW)).isFalse();
        }

        @Test
        void a_password_change_clears_the_lock() {
            AdminAccount admin = account();
            for (int i = 0; i < AdminAccount.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
                admin.recordFailedLogin(NOW);
            }

            admin.changePassword("hashed:new", NOW);

            assertThat(admin.isLocked(NOW)).isFalse();
            assertThat(admin.passwordHash()).isEqualTo("hashed:new");
        }
    }

    @Test
    void a_deactivated_account_is_refused() {
        AdminAccount admin = account();
        admin.deactivate(NOW);

        assertThatThrownBy(() -> admin.assertUsable(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void rehydrate_restores_state_without_side_effects() {
        UUID id = UUID.randomUUID();
        AdminAccount admin = AdminAccount.rehydrate(id, "Bob Rao", EmailAddress.of("bob@choco.example"),
                "hashed:x", AdminRole.ADMIN, false, 3, NOW.plusSeconds(600), NOW, NOW);

        assertThat(admin.id()).isEqualTo(id);
        assertThat(admin.role()).isEqualTo(AdminRole.ADMIN);
        assertThat(admin.isActive()).isFalse();
        assertThat(admin.failedLoginAttempts()).isEqualTo(3);
        assertThat(admin.lockedUntil()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void role_levels_are_distinguishable() {
        assertThat(AdminRole.SUPER_ADMIN.isSuperAdmin()).isTrue();
        assertThat(AdminRole.ADMIN.isSuperAdmin()).isFalse();
    }
}
