package com.choco.notification.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the database the notification service actually runs against.
 *
 * <p>This is what a unit test cannot see. The migration is applied to a real PostgreSQL and Hibernate
 * runs in {@code ddl-auto=validate}, so an entity column missing from the migration fails the context
 * at startup. The constraints are then asserted directly, because the guarantees that matter here — a
 * dedupe key that cannot repeat, and a status that can only be a known value — are ones a future code
 * path must not be able to walk around.
 */
@DisplayName("Notification schema and persistence (real PostgreSQL)")
class SchemaAndPersistenceIT extends AbstractNotificationIntegrationTest {

    @Test
    @DisplayName("the migration has been applied by Flyway")
    void flyway_applied_the_migration() throws Exception {
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
        assertThat(versions).contains("1");
    }

    @Test
    @DisplayName("every notification table exists")
    void all_tables_exist() throws Exception {
        assertThat(tableNames()).contains(
                "notifications", "customer_contacts", "audit_log", "outbox_event");
    }

    @Test
    @DisplayName("a duplicate dedupe key is refused by the database")
    void dedupe_key_is_unique() throws Exception {
        insertNotification(UUID.randomUUID(), "order.confirmed:order-1");

        assertThatThrownBy(() -> insertNotification(UUID.randomUUID(), "order.confirmed:order-1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("notifications_dedupe_key");
    }

    @Test
    @DisplayName("the customer-contact projection is keyed by the identity customer id")
    void contact_projection_is_keyed_by_customer_id() throws Exception {
        UUID customerId = UUID.randomUUID();
        insertContact(customerId, "asha@example.com");

        assertThatThrownBy(() -> insertContact(customerId, "other@example.com"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("customer_contacts_pkey");
    }

    @Test
    @DisplayName("no column in the notification schema can hold a password hash or a token")
    void no_column_stores_credentials() throws Exception {
        // A blunt but effective guard: the schema must never grow a column whose name suggests it
        // holds a credential. This service records what it sent, and nothing it must not store.
        List<String> suspicious = new ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT table_name, column_name FROM information_schema.columns "
                             + "WHERE table_schema = 'public'")) {
            while (rows.next()) {
                String column = rows.getString("column_name").toLowerCase();
                if (column.contains("password") || column.contains("secret") || column.contains("token")) {
                    suspicious.add(rows.getString("table_name") + "." + column);
                }
            }
        }
        assertThat(suspicious).isEmpty();
    }

    // --- helpers -------------------------------------------------------------------------------

    private List<String> tableNames() throws Exception {
        List<String> tables = new ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        return tables;
    }

    private void insertNotification(UUID id, String dedupeKey) throws SQLException {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO notifications (id, type, channel, recipient_type, recipient, subject,
                        body, dedupe_key, status, attempts, created_at, updated_at, version)
                    VALUES ('%s', 'ORDER_CONFIRMED', 'EMAIL', 'CUSTOMER', 'asha@example.com', 's',
                        'b', '%s', 'PENDING', 0, now(), now(), 0)
                    """.formatted(id, dedupeKey));
        }
    }

    private void insertContact(UUID customerId, String email) throws SQLException {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO customer_contacts (customer_id, email, updated_at)
                    VALUES ('%s', '%s', now())
                    """.formatted(customerId, email));
        }
    }
}
