package com.choco.fulfilment.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the database the fulfilment service actually runs against.
 *
 * <p>This is the class that catches what a unit test is blind to. The migration is applied to a real
 * PostgreSQL and Hibernate is left in {@code ddl-auto=validate}, so a column present in an entity but
 * missing from the migration fails the context at startup. The constraints are then asserted directly,
 * because a rule that lives only in the application layer is one a future code path can walk straight
 * past.
 */
@DisplayName("Fulfilment schema and persistence (real PostgreSQL)")
class SchemaAndPersistenceIT extends AbstractFulfilmentIntegrationTest {

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
    @DisplayName("every fulfilment table exists")
    void all_tables_exist() throws Exception {
        assertThat(tableNames()).contains(
                "production_jobs", "production_notes", "ingredients", "recipe_lines",
                "stock_ledger_entries", "serviceable_areas", "deliveries", "delivery_attempts",
                "outbox_event", "audit_log");
    }

    @Test
    @DisplayName("a production job's quantity must be positive")
    void job_quantity_must_be_positive() throws Exception {
        assertThatThrownBy(() -> insertJob("11111111-1111-1111-1111-111111111111", 0))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("production_jobs_quantity_positive");
    }

    @Test
    @DisplayName("one job per order line: the same variant twice on one order is refused")
    void one_job_per_order_variant() throws Exception {
        insertJob("22222222-2222-2222-2222-222222222222", 1);
        assertThatThrownBy(() -> insertJob("33333333-3333-3333-3333-333333333333", 1))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("production_jobs_order_variant_unique");
    }

    @Test
    @DisplayName("a job's currency can only be INR")
    void job_currency_must_be_inr() throws Exception {
        assertThatThrownBy(() -> insertJobWithCurrency("44444444-4444-4444-4444-444444444444", "USD"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("production_jobs_currency_inr");
    }

    @Test
    @DisplayName("deleting a job cascades to its notes")
    void deleting_a_job_cascades_to_notes() throws Exception {
        String jobId = "55555555-5555-5555-5555-555555555555";
        insertJob(jobId, 1);
        insertNote("66666666-6666-6666-6666-666666666666", jobId);

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("DELETE FROM production_jobs WHERE id = '" + jobId + "'");
            assertThat(countWhere(statement, "production_notes", "job_id", jobId)).isZero();
        }
    }

    @Test
    @DisplayName("an ingredient level can never go negative")
    void ingredient_level_cannot_go_negative() throws Exception {
        assertThatThrownBy(() -> insertIngredient("77777777-7777-7777-7777-777777777777", -1))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ingredients_on_hand_non_negative");
    }

    @Test
    @DisplayName("ingredient names are unique case-insensitively")
    void ingredient_names_are_unique_case_insensitively() throws Exception {
        insertIngredientNamed("88888888-8888-8888-8888-888888888888", "Cocoa Butter");
        assertThatThrownBy(() -> insertIngredientNamed("99999999-9999-9999-9999-999999999999",
                "cocoa butter"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ingredients_name_unique");
    }

    @Test
    @DisplayName("a stock ledger movement of zero is refused")
    void a_zero_stock_movement_is_refused() throws Exception {
        String ingredientId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        insertIngredient(ingredientId, 100);
        assertThatThrownBy(() -> insertLedger("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", ingredientId, 0, 100))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("stock_ledger_delta_non_zero");
    }

    @Test
    @DisplayName("a delivery area prefix must be a PIN code or a leading part of one")
    void area_prefix_must_be_a_valid_pincode_prefix() throws Exception {
        assertThatThrownBy(() -> insertArea("cccccccc-cccc-cccc-cccc-cccccccccccc", "012345"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("serviceable_areas_prefix_format");
    }

    @Test
    @DisplayName("one delivery per order")
    void one_delivery_per_order() throws Exception {
        insertDelivery("dddddddd-dddd-dddd-dddd-dddddddddddd");
        assertThatThrownBy(() -> insertDelivery("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("deliveries_order_unique");
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

    private void insertJob(String id, int quantity) throws Exception {
        insertJob(id, quantity, "INR");
    }

    private void insertJobWithCurrency(String id, String currency) throws Exception {
        insertJob(id, 1, currency);
    }

    private void insertJob(String id, int quantity, String currency) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO production_jobs (id, order_id, order_number, customer_id, product_id,
                        variant_id, variant_label, quantity, delivery_charge_minor_units, currency,
                        status, ingredients_consumed, received_at, updated_at, version)
                    VALUES ('%s', '12345678-1234-1234-1234-123456789012', 'CHOCO-1',
                        '22345678-1234-1234-1234-123456789012', '32345678-1234-1234-1234-123456789012',
                        '42345678-1234-1234-1234-123456789012', '200 g', %d, 5000, '%s',
                        'RECEIVED', false, now(), now(), 0)
                    """.formatted(id, quantity, currency));
        }
    }

    private void insertNote(String id, String jobId) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO production_notes (id, job_id, text, created_at, display_order)
                    VALUES ('%s', '%s', 'careful with the tempering', now(), 0)
                    """.formatted(id, jobId));
        }
    }

    private void insertIngredient(String id, long onHand) throws Exception {
        insertIngredientNamed(id, "Cocoa Butter", onHand);
    }

    private void insertIngredientNamed(String id, String name) throws Exception {
        insertIngredientNamed(id, name, 100);
    }

    private void insertIngredientNamed(String id, String name, long onHand) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO ingredients (id, name, unit, on_hand_milli_units, minimum_milli_units,
                        active, created_at, updated_at, version)
                    VALUES ('%s', '%s', 'GRAM', %d, 0, true, now(), now(), 0)
                    """.formatted(id, name, onHand));
        }
    }

    private void insertLedger(String id, String ingredientId, long delta, long balance) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO stock_ledger_entries (id, ingredient_id, reason, delta_milli_units,
                        balance_after_milli_units, occurred_at)
                    VALUES ('%s', '%s', 'RECEIVED', %d, %d, now())
                    """.formatted(id, ingredientId, delta, balance));
        }
    }

    private void insertArea(String id, String prefix) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO serviceable_areas (id, pincode_prefix, area_name,
                        delivery_charge_minor_units, currency, active, created_at, updated_at, version)
                    VALUES ('%s', '%s', 'Somewhere', 5000, 'INR', true, now(), now(), 0)
                    """.formatted(id, prefix));
        }
    }

    private void insertDelivery(String id) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO deliveries (id, order_id, order_number, customer_id,
                        address_recipient_name, address_mobile, address_line1, address_city,
                        address_state, address_pincode, delivery_charge_minor_units, currency,
                        status, created_at, updated_at, version)
                    VALUES ('%s', '12345678-1234-1234-1234-123456789012', 'CHOCO-1',
                        '22345678-1234-1234-1234-123456789012', 'Asha Rao', '9876543210',
                        '12 Cocoa Lane', 'Bengaluru', 'Karnataka', '560001', 5000, 'INR',
                        'PENDING', now(), now(), 0)
                    """.formatted(id));
        }
    }

    private static long countWhere(java.sql.Statement statement, String table, String column,
                                   String value) throws SQLException {
        try (var rows = statement.executeQuery(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = '" + value + "'")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
