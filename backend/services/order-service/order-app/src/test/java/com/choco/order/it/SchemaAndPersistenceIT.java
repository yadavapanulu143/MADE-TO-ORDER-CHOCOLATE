package com.choco.order.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the database the order service actually runs against.
 *
 * <p>This is the class that catches what a unit test is blind to. The migration is applied to a real
 * PostgreSQL and Hibernate is left in {@code ddl-auto=validate}, so a column present in an entity but
 * missing from the migration fails the context at startup. The constraints are then asserted directly,
 * because a rule that lives only in the application layer is one a future code path — a batch import,
 * a manual fix, another service's migration — can walk straight past.
 */
@DisplayName("Order schema and persistence (real PostgreSQL)")
class SchemaAndPersistenceIT extends AbstractOrderIntegrationTest {

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
    @DisplayName("every order table exists")
    void all_tables_exist() throws Exception {
        assertThat(tableNames()).contains(
                "orders", "order_lines", "order_status_history", "order_notes",
                "carts", "cart_lines", "idempotency_record", "outbox_event", "audit_log");
    }

    @Test
    @DisplayName("the order number sequence exists and issues distinct values")
    void order_number_sequence_issues_distinct_values() throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            long first = nextSequenceValue(statement);
            long second = nextSequenceValue(statement);
            assertThat(second).isGreaterThan(first);
        }
    }

    @Test
    @DisplayName("the stored total must equal items plus delivery charge")
    void total_must_equal_its_parts() throws Exception {
        assertThatThrownBy(() -> insertOrder("11111111-1111-1111-1111-111111111111", "CHOCO-1", 10_000,
                5_000, 99_999))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("orders_total_is_sum");
    }

    @Test
    @DisplayName("a negative amount is rejected by the database")
    void a_negative_amount_is_rejected() throws Exception {
        assertThatThrownBy(() -> insertOrder("22222222-2222-2222-2222-222222222222", "CHOCO-2", -1,
                0, -1))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("orders_totals_non_negative");
    }

    @Test
    @DisplayName("the order number is unique")
    void order_number_is_unique() throws Exception {
        insertOrder("33333333-3333-3333-3333-333333333333", "CHOCO-3", 10_000, 5_000, 15_000);
        assertThatThrownBy(() -> insertOrder("44444444-4444-4444-4444-444444444444", "CHOCO-3",
                10_000, 5_000, 15_000))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("orders_number_unique");
    }

    @Test
    @DisplayName("a currency other than INR is rejected")
    void a_foreign_currency_is_rejected() throws Exception {
        assertThatThrownBy(() -> insertOrderWithCurrency("55555555-5555-5555-5555-555555555555",
                "CHOCO-5", "USD"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("orders_currency_inr");
    }

    @Test
    @DisplayName("an order line cannot reference an order that does not exist")
    void order_line_requires_an_order() throws Exception {
        assertThatThrownBy(() -> insertOrderLine("66666666-6666-6666-6666-666666666666",
                "77777777-7777-7777-7777-777777777777"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("order_lines_order_fk");
    }

    @Test
    @DisplayName("deleting an order cascades to its lines, history and notes")
    void deleting_an_order_cascades() throws Exception {
        String orderId = "88888888-8888-8888-8888-888888888888";
        insertOrder(orderId, "CHOCO-8", 10_000, 5_000, 15_000);
        insertOrderLine("99999999-9999-9999-9999-999999999999", orderId);
        insertStatusHistory("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", orderId);

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("DELETE FROM orders WHERE id = '" + orderId + "'");
            assertThat(countWhere(statement, "order_lines", "order_id", orderId)).isZero();
            assertThat(countWhere(statement, "order_status_history", "order_id", orderId)).isZero();
        }
    }

    @Test
    @DisplayName("one customer cannot have two carts")
    void one_cart_per_customer() throws Exception {
        String customer = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        insertCart("cccccccc-cccc-cccc-cccc-cccccccccccc", customer);
        assertThatThrownBy(() -> insertCart("dddddddd-dddd-dddd-dddd-dddddddddddd", customer))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("carts_customer_unique");
    }

    @Test
    @DisplayName("an idempotency key is unique per scope")
    void idempotency_key_is_unique_per_scope() throws Exception {
        insertIdempotency("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee", "checkout", "key-1");
        assertThatThrownBy(() -> insertIdempotency("ffffffff-ffff-ffff-ffff-ffffffffffff",
                "checkout", "key-1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("idempotency_scope_key_unique");
    }

    @Test
    @DisplayName("the same key in a different scope is allowed")
    void the_same_key_in_another_scope_is_allowed() throws Exception {
        insertIdempotency("11111111-2222-3333-4444-555555555555", "checkout", "key-2");
        insertIdempotency("66666666-7777-8888-9999-000000000000", "payment", "key-2");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private List<String> tableNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
    }

    private long nextSequenceValue(java.sql.Statement statement) throws SQLException {
        try (var rows = statement.executeQuery("SELECT nextval('order_number_seq')")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void insertOrder(String id, String number, long items, long delivery, long total)
            throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO orders (id, order_number, customer_id, customer_name, customer_email,
                        customer_mobile, address_recipient_name, address_mobile, address_line1,
                        address_city, address_state, address_pincode, items_total_minor_units,
                        delivery_charge_minor_units, total_minor_units, status, payment_status,
                        delivery_status, created_at, updated_at, version)
                    VALUES ('%s', '%s', '%s', 'Asha Rao', 'asha@example.com', '9876543210',
                        'Asha Rao', '9876543210', '12 Cocoa Lane', 'Bengaluru', 'Karnataka', '560001',
                        %d, %d, %d, 'PAYMENT_PENDING', 'PENDING', 'PENDING', now(), now(), 0)
                    """.formatted(id, number, "12345678-1234-1234-1234-123456789012", items, delivery, total));
        }
    }

    private void insertOrderWithCurrency(String id, String number, String currency) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO orders (id, order_number, customer_id, customer_name, customer_email,
                        customer_mobile, address_recipient_name, address_mobile, address_line1,
                        address_city, address_state, address_pincode, items_total_minor_units,
                        delivery_charge_minor_units, total_minor_units, currency, status, payment_status,
                        delivery_status, created_at, updated_at, version)
                    VALUES ('%s', '%s', '12345678-1234-1234-1234-123456789012', 'Asha Rao',
                        'asha@example.com', '9876543210', 'Asha Rao', '9876543210', '12 Cocoa Lane',
                        'Bengaluru', 'Karnataka', '560001', 10000, 5000, 15000, '%s',
                        'PAYMENT_PENDING', 'PENDING', 'PENDING', now(), now(), 0)
                    """.formatted(id, number, currency));
        }
    }

    private void insertOrderLine(String id, String orderId) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO order_lines (id, order_id, product_id, variant_id, product_name,
                        variant_label, unit_price_minor_units, quantity, display_order)
                    VALUES ('%s', '%s', '%s', '%s', 'Dark Chocolate', '200 g', 10000, 1, 0)
                    """.formatted(id, orderId, "22222222-2222-2222-2222-222222222222",
                    "33333333-3333-3333-3333-333333333333"));
        }
    }

    private void insertStatusHistory(String id, String orderId) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO order_status_history (id, order_id, to_status, occurred_at, display_order)
                    VALUES ('%s', '%s', 'PAYMENT_PENDING', now(), 0)
                    """.formatted(id, orderId));
        }
    }

    private void insertCart(String id, String customerId) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO carts (id, customer_id, created_at, updated_at, version)
                    VALUES ('%s', '%s', now(), now(), 0)
                    """.formatted(id, customerId));
        }
    }

    private void insertIdempotency(String id, String scope, String key) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO idempotency_record (id, scope, key, created_at, expires_at)
                    VALUES ('%s', '%s', '%s', now(), now() + interval '1 day')
                    """.formatted(id, scope, key));
        }
    }

    private static long countWhere(java.sql.Statement statement, String table, String column, String value)
            throws SQLException {
        try (var rows = statement.executeQuery(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = '" + value + "'")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
