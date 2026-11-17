package com.choco.payment.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the database the payment service actually runs against.
 *
 * <p>The migration is applied to a real PostgreSQL and Hibernate is left in {@code ddl-auto=validate},
 * so a column present in an entity but missing from the migration fails the context at startup. The
 * constraints are then asserted directly: a rule that lives only in the application layer is one a
 * future code path — a webhook, a manual fix, a migration in another service — can walk straight past.
 */
@DisplayName("Payment schema and persistence (real PostgreSQL)")
class SchemaAndPersistenceIT extends AbstractPaymentIntegrationTest {

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
    @DisplayName("every payment table exists")
    void all_tables_exist() throws Exception {
        assertThat(tableNames()).contains(
                "payments", "payment_attempts", "refunds", "disputes",
                "idempotency_record", "outbox_event", "audit_log");
    }

    @Test
    @DisplayName("one order can have only one payment, enforced by a unique index")
    void one_payment_per_order() throws Exception {
        UUID orderId = UUID.randomUUID();
        insertPayment(UUID.randomUUID(), orderId, "po_a", 49_900);

        assertThatThrownBy(() -> insertPayment(UUID.randomUUID(), orderId, "po_b", 49_900))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("payments_order_unique");
    }

    @Test
    @DisplayName("two payments cannot share a provider order")
    void provider_order_is_unique() throws Exception {
        insertPayment(UUID.randomUUID(), UUID.randomUUID(), "po_shared", 49_900);

        assertThatThrownBy(() -> insertPayment(UUID.randomUUID(), UUID.randomUUID(), "po_shared", 49_900))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("payments_provider_order_unique");
    }

    @Test
    @DisplayName("a non-positive amount is rejected by the database")
    void non_positive_amount_rejected() throws Exception {
        assertThatThrownBy(() -> insertPayment(UUID.randomUUID(), UUID.randomUUID(), "po_neg", 0))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("payments_amount_positive");
    }

    @Test
    @DisplayName("a currency other than INR is rejected by the database")
    void non_inr_currency_rejected() throws Exception {
        assertThatThrownBy(() -> insertPayment(UUID.randomUUID(), UUID.randomUUID(), "po_usd", 49_900, "USD"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("payments_currency_inr");
    }

    @Test
    @DisplayName("a refund must reference a real payment")
    void refund_requires_a_payment() throws Exception {
        assertThatThrownBy(() -> insertRefund(UUID.randomUUID(), UUID.randomUUID(), 10_000))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("refunds_payment_id_fkey");
    }

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

    private void insertPayment(UUID id, UUID orderId, String providerOrderId, long amount)
            throws Exception {
        insertPayment(id, orderId, providerOrderId, amount, "INR");
    }

    private void insertPayment(UUID id, UUID orderId, String providerOrderId, long amount, String currency)
            throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO payments (id, order_id, customer_id, amount_minor_units, currency,
                        provider, provider_order_id, status, created_at, updated_at, version)
                    VALUES ('%s', '%s', '%s', %d, '%s', 'razorpay', '%s', 'CREATED', now(), now(), 0)
                    """.formatted(id, orderId, UUID.randomUUID(), amount, currency, providerOrderId));
        }
    }

    private void insertRefund(UUID id, UUID paymentId, long amount) throws Exception {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO refunds (id, payment_id, amount_minor_units, currency, status, requested_at)
                    VALUES ('%s', '%s', %d, 'INR', 'REQUESTED', now())
                    """.formatted(id, paymentId, amount));
        }
    }
}
