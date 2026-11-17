package com.choco.reporting.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the database the reporting service actually runs against.
 *
 * <p>This is what a unit test cannot see. The migration is applied to a real PostgreSQL and Hibernate
 * runs in {@code ddl-auto=validate}, so an entity column missing from the migration fails the context at
 * startup. The constraints are then asserted directly, because the guarantees that matter — a projection
 * keyed on the owning service's identifier, so a replayed event updates one row — are ones a future code
 * path must not be able to walk around.
 */
@DisplayName("Reporting schema and persistence (real PostgreSQL)")
class SchemaAndPersistenceIT extends AbstractReportingIntegrationTest {

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
    @DisplayName("every projection table exists")
    void all_tables_exist() throws Exception {
        assertThat(tableNames()).contains(
                "order_projection", "payment_projection", "production_projection",
                "delivery_projection", "refund_projection", "customer_projection",
                "invoice_projection", "audit_log", "outbox_event");
    }

    @Test
    @DisplayName("a projection is keyed on the owning service's id, so a second row is refused")
    void projection_primary_key_is_the_producer_id() throws Exception {
        insertOrder("order-1");

        assertThatThrownBy(() -> insertOrder("order-1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("order_projection");
    }

    @Test
    @DisplayName("a projection cannot be stored without its required columns")
    void required_columns_are_not_null() throws Exception {
        assertThatThrownBy(() -> execute("""
                INSERT INTO order_projection (order_id, status, total_minor_units, currency, placed_at)
                VALUES ('order-x', NULL, 0, 'INR', now())
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("there is no column anywhere that could hold a credential")
    void no_credential_columns() throws Exception {
        List<String> columns = new ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT table_name, column_name FROM information_schema.columns
                     WHERE table_schema = 'public'
                     """)) {
            while (rows.next()) {
                columns.add((rows.getString("table_name") + "." + rows.getString("column_name"))
                        .toLowerCase(java.util.Locale.ROOT));
            }
        }
        assertThat(columns).isNotEmpty();
        assertThat(columns).noneMatch(column ->
                column.contains("password") || column.contains("hash") || column.contains("otp")
                        || column.contains("token") || column.contains("secret")
                        || column.contains("cvv") || column.contains("pin"));
    }

    private void insertOrder(String orderId) throws Exception {
        execute("""
                INSERT INTO order_projection (order_id, status, total_minor_units, currency, placed_at)
                VALUES ('%s', 'PLACED', 100, 'INR', now())
                """.formatted(orderId));
    }

    private void execute(String sql) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<String> tableNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                     """)) {
            while (rows.next()) {
                names.add(rows.getString("table_name"));
            }
        }
        return names;
    }
}
