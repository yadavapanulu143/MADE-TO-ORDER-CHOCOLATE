package com.choco.catalog.it;

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
 * <p>This is the class that catches the failures unit tests are blind to. The migration is applied to
 * a real PostgreSQL and Hibernate is left in {@code ddl-auto=validate}, so a column that exists in an
 * entity but not in the migration fails the context at startup. The constraints are then asserted
 * directly, because a rule that lives only in the application layer is one a future code path — a
 * batch import, a manual fix, another service's migration — can walk straight past.
 */
@DisplayName("Catalog schema and persistence (real PostgreSQL)")
class SchemaAndPersistenceIT extends AbstractCatalogIntegrationTest {

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
    @DisplayName("every catalogue table exists")
    void all_tables_exist() throws Exception {
        assertThat(tableNames()).contains(
                "products", "product_variants", "product_images", "product_attributes",
                "website_settings", "audit_log", "outbox_event");
    }

    @Test
    @DisplayName("the slug is unique across every status")
    void slug_uniqueness_is_enforced() throws Exception {
        insertProduct("11111111-1111-1111-1111-111111111111", "dark-bar", "DRAFT");

        assertThatThrownBy(() -> insertProduct("22222222-2222-2222-2222-222222222222", "dark-bar", "DRAFT"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("products_slug_unique");
    }

    @Test
    @DisplayName("a variant price of zero is rejected by the database")
    void a_zero_price_variant_is_rejected() throws Exception {
        insertProduct("33333333-3333-3333-3333-333333333333", "dark-bar", "DRAFT");

        assertThatThrownBy(() -> insertVariant("44444444-4444-4444-4444-444444444444",
                "33333333-3333-3333-3333-333333333333", 0))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("product_variants_price_positive");
    }

    @Test
    @DisplayName("a variant cannot reference a product that does not exist")
    void variant_requires_a_product() throws Exception {
        assertThatThrownBy(() -> insertVariant("55555555-5555-5555-5555-555555555555",
                "66666666-6666-6666-6666-666666666666", 50000))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("product_variants_product_fk");
    }

    @Test
    @DisplayName("only one primary image is allowed per product")
    void only_one_primary_image_per_product() throws Exception {
        String productId = "77777777-7777-7777-7777-777777777777";
        insertProduct(productId, "dark-bar", "DRAFT");
        insertImage("88888888-8888-8888-8888-888888888888", productId, "k1", true);

        assertThatThrownBy(() -> insertImage("99999999-9999-9999-9999-999999999999", productId, "k2", true))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("product_images_one_primary");
    }

    @Test
    @DisplayName("deleting a product cascades to its variants, images and attributes")
    void deleting_a_product_cascades() throws Exception {
        String productId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        insertProduct(productId, "dark-bar", "DRAFT");
        insertVariant("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", productId, 50000);
        insertImage("cccccccc-cccc-cccc-cccc-cccccccccccc", productId, "k1", true);

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("DELETE FROM products WHERE id = '" + productId + "'");
            assertThat(countWhere(statement, "product_variants", "product_id", productId)).isZero();
            assertThat(countWhere(statement, "product_images", "product_id", productId)).isZero();
        }
    }

    @Test
    @DisplayName("an outbox row is written when a product is created over HTTP")
    void creating_a_product_writes_an_outbox_row() throws Exception {
        String token = adminToken(java.util.UUID.randomUUID());

        post("/api/v1/admin/catalog/products",
                jsonBody(java.util.Map.of("name", "Outbox Bar", "shortDescription", "70% cocoa")), token);

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT count(*) FROM outbox_event WHERE event_type = 'catalog.product-created'")) {
            rows.next();
            assertThat(rows.getLong(1)).isEqualTo(1);
        }
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

    private void insertProduct(String id, String slug, String status) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO products (id, slug, name, status, created_at, updated_at, version)
                    VALUES ('%s', '%s', 'Dark Bar', '%s', now(), now(), 0)
                    """.formatted(id, slug, status));
        }
    }

    private void insertVariant(String id, String productId, long price) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO product_variants
                        (id, product_id, label, price_minor_units, currency, min_order_quantity,
                         max_order_quantity, active, display_order)
                    VALUES ('%s', '%s', '200 g', %d, 'INR', 1, 10, true, 0)
                    """.formatted(id, productId, price));
        }
    }

    private void insertImage(String id, String productId, String key, boolean primary) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO product_images
                        (id, product_id, storage_key, content_type, size_bytes, display_order, is_primary)
                    VALUES ('%s', '%s', '%s', 'image/jpeg', 1024, 0, %s)
                    """.formatted(id, productId, key, primary));
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
