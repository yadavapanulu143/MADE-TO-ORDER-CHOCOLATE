package com.choco.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the database the service actually runs against.
 *
 * <p>This is the class that catches the failures unit tests are blind to. The migrations are applied
 * to a real PostgreSQL and Hibernate is left in {@code ddl-auto=validate}, so a column renamed in the
 * entities but not in a migration fails the context at startup. The constraints are then asserted
 * directly, because a rule that only exists in the application layer is one a future code path — a
 * batch job, a manual fix, another service's migration — can walk straight past.
 */
@DisplayName("Identity schema and persistence (real PostgreSQL)")
class SchemaAndPersistenceIT extends AbstractIdentityIntegrationTest {

    @Test
    @DisplayName("every migration has been applied by Flyway")
    void flyway_applied_all_migrations() throws Exception {
        List<String> versions = new ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rows.next()) {
                assertThat(rows.getBoolean("success"))
                        .as("migration %s must have applied cleanly", rows.getString("version"))
                        .isTrue();
                versions.add(rows.getString("version"));
            }
        }
        assertThat(versions).contains("1", "2", "3");
    }

    @Test
    @DisplayName("the admin tables and the customer tables coexist in one schema")
    void both_aggregates_have_their_tables() throws Exception {
        assertThat(tableNames()).contains(
                "customers", "customer_verified_channels", "otp_challenges", "refresh_tokens",
                "password_reset_tokens", "customer_addresses", "audit_log", "outbox_event",
                "admin_accounts", "admin_refresh_tokens");
    }

    @Test
    @DisplayName("a second customer with the same email is rejected by the database")
    void email_uniqueness_is_enforced_case_insensitively() throws Exception {
        insertCustomer("11111111-1111-1111-1111-111111111111", "First@Example.test",
                "9000000001", "hash");

        // Different case: the unique index is on lower(email), so this must still collide. The
        // application normalises the value, but the index is the guarantee, not the normalisation.
        assertThatThrownBy(() ->
                insertCustomer("22222222-2222-2222-2222-222222222222", "first@example.test",
                        "9000000002", "hash"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("customers_email_unique");
    }

    @Test
    @DisplayName("a malformed mobile number is rejected by the check constraint")
    void mobile_format_is_enforced_by_the_database() {
        assertThatThrownBy(() ->
                insertCustomer("33333333-3333-3333-3333-333333333333", "bad@example.test",
                        "12345", "hash"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("customers_mobile_format");
    }

    @Test
    @DisplayName("an administrator cannot hold a role outside the two defined levels")
    void admin_role_is_constrained() throws Exception {
        assertThatThrownBy(() -> insertAdmin("44444444-4444-4444-4444-444444444444",
                "root@choco.test", "hash", "SUPERUSER"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("admin_accounts_role_valid");
    }

    @Test
    @DisplayName("a refresh token cannot outlive its issue time")
    void refresh_token_expiry_must_be_after_issue() throws Exception {
        insertCustomer("55555555-5555-5555-5555-555555555555", "token@example.test",
                "9000000005", "hash");

        assertThatThrownBy(() -> {
            try (var connection = POSTGRES.createConnection("");
                 var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO refresh_tokens (id, customer_id, token_hash, issued_at, expires_at)
                        VALUES ('66666666-6666-6666-6666-666666666666',
                                '55555555-5555-5555-5555-555555555555', 'abc',
                                now(), now() - interval '1 hour')
                        """);
            }
        }).isInstanceOf(SQLException.class)
                .hasMessageContaining("refresh_tokens_expiry_after_issue");
    }

    @Test
    @DisplayName("an address for an unknown customer is rejected by the foreign key")
    void addresses_require_an_existing_customer() {
        assertThatThrownBy(() -> {
            try (var connection = POSTGRES.createConnection("");
                 var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO customer_addresses (id, customer_id, recipient_name, mobile, line1,
                            city, state, pincode, is_default, created_at, updated_at)
                        VALUES ('77777777-7777-7777-7777-777777777777',
                                '88888888-8888-8888-8888-888888888888', 'Nobody', '9000000007',
                                'line', 'City', 'State', '560001', false, now(), now())
                        """);
            }
        }).isInstanceOf(SQLException.class)
                .hasMessageContaining("customer_addresses_customer_fk");
    }

    // ---------------------------------------------------------------------------------------------

    private List<String> tableNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rows.next()) {
                names.add(rows.getString("table_name"));
            }
        }
        return names;
    }

    private void insertCustomer(String id, String email, String mobile, String hash) throws SQLException {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO customers (id, full_name, email, mobile, password_hash,
                        registered_at, updated_at, failed_login_attempts, active, version)
                    VALUES ('%s', 'Test', '%s', '%s', '%s', now(), now(), 0, true, 0)
                    """.formatted(id, email, mobile, hash));
        }
    }

    private void insertAdmin(String id, String email, String hash, String role) throws SQLException {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO admin_accounts (id, full_name, email, password_hash, role,
                        active, failed_login_attempts, created_at, updated_at, version)
                    VALUES ('%s', 'Root', '%s', '%s', '%s', true, 0, now(), now(), 0)
                    """.formatted(id, email, hash, role));
        }
    }
}
