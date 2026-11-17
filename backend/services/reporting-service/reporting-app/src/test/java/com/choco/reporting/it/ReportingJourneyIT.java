package com.choco.reporting.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.events.envelope.EventTypes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reporting journey over a real broker and a real database.
 *
 * <p>The projections are only correct if the consumer's binding, its header dispatch and the payload
 * field names all line up with what the producers actually publish. A unit test with a hand-built map
 * cannot catch a mismatched field name, and that is exactly the failure that would leave a report
 * showing zeros while every test passed. Here the events are published to the real exchange and the
 * assertions read the real tables.
 */
@DisplayName("Reporting journey (real RabbitMQ, real PostgreSQL)")
class ReportingJourneyIT extends AbstractReportingIntegrationTest {

    private static final String ORDER = "order-1";

    @Test
    @DisplayName("order events project an order and advance it without adding a second row")
    void orderEventsProjectOneRow() {
        publish(EventTypes.ORDER_PLACED, ORDER, Map.of(
                "orderId", ORDER, "customerId", "cust-1", "orderNumber", "ORD-1",
                "totalMinorUnits", 125_000L, "currency", "INR", "lineCount", 2));
        awaitProjection("SELECT status FROM order_projection WHERE order_id = '" + ORDER + "'", "PLACED");

        publish(EventTypes.ORDER_CONFIRMED, ORDER, Map.of(
                "orderId", ORDER, "customerId", "cust-1", "orderNumber", "ORD-1",
                "deliveryChargeMinorUnits", 5_000L, "currency", "INR"));
        awaitProjection("SELECT status FROM order_projection WHERE order_id = '" + ORDER + "'", "CONFIRMED");

        assertThat(count("order_projection")).isEqualTo(1);
        assertThat(query("SELECT delivery_charge_minor_units FROM order_projection WHERE order_id = '"
                + ORDER + "'")).isEqualTo("5000");
    }

    @Test
    @DisplayName("a payment capture projects a PAID payment keyed on the payment id")
    void paymentCaptureProjects() {
        publish(EventTypes.PAYMENT_CAPTURED, "pay-1", Map.of(
                "aggregateId", "pay-1", "orderId", ORDER, "customerId", "cust-1",
                "amountMinorUnits", 125_000L, "currency", "INR", "method", "UPI"));

        awaitProjection("SELECT status FROM payment_projection WHERE payment_id = 'pay-1'", "PAID");
        assertThat(query("SELECT amount_minor_units FROM payment_projection WHERE payment_id = 'pay-1'"))
                .isEqualTo("125000");
    }

    @Test
    @DisplayName("production events store the kitchen's own status name")
    void productionStoresTheKitchenStatus() {
        publish(EventTypes.PRODUCTION_STARTED, "job-1", Map.of(
                "jobId", "job-1", "orderId", ORDER, "orderNumber", "ORD-1", "customerId", "cust-1",
                "variantLabel", "Dark 70%", "quantity", 2, "status", "PREPARATION_STARTED"));

        awaitProjection("SELECT status FROM production_projection WHERE job_id = 'job-1'",
                "PREPARATION_STARTED");
    }

    @Test
    @DisplayName("delivery events advance one row keyed on the delivery id")
    void deliveryAdvancesOneRow() {
        publish(EventTypes.DELIVERY_ASSIGNED, "del-1", Map.of(
                "deliveryId", "del-1", "orderId", ORDER, "orderNumber", "ORD-1", "customerId", "cust-1",
                "status", "ASSIGNED", "deliveryPersonName", "Ravi"));
        awaitProjection("SELECT status FROM delivery_projection WHERE delivery_id = 'del-1'", "ASSIGNED");

        publish(EventTypes.DELIVERY_FAILED, "del-1", Map.of(
                "deliveryId", "del-1", "status", "ATTEMPT_FAILED", "failureReason", "not at home",
                "attemptNumber", 1));
        awaitProjection("SELECT status FROM delivery_projection WHERE delivery_id = 'del-1'",
                "ATTEMPT_FAILED");

        assertThat(count("delivery_projection")).isEqualTo(1);
    }

    @Test
    @DisplayName("a redelivered event updates the row rather than adding a second")
    void redeliveryIsIdempotent() {
        Map<String, Object> payload = Map.of(
                "orderId", ORDER, "customerId", "cust-1", "orderNumber", "ORD-1",
                "totalMinorUnits", 125_000L, "currency", "INR", "lineCount", 1);
        publish(EventTypes.ORDER_PLACED, ORDER, payload);
        awaitProjection("SELECT status FROM order_projection WHERE order_id = '" + ORDER + "'", "PLACED");
        publish(EventTypes.ORDER_PLACED, ORDER, payload);

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(count("order_projection")).isEqualTo(1));
    }

    @Test
    @DisplayName("a refund request and its processing update one row, so a refund is not counted twice")
    void refundRequestThenProcessed() {
        publish(EventTypes.REFUND_REQUESTED, ORDER, Map.of(
                "orderId", ORDER, "customerId", "cust-1", "orderNumber", "ORD-1",
                "amountMinorUnits", 5_000L, "currency", "INR", "reason", "damaged"));
        awaitProjection("SELECT status FROM refund_projection WHERE refund_id = 'refund-" + ORDER + "'",
                "REQUESTED");

        publish(EventTypes.REFUND_PROCESSED, "pay-1", Map.of(
                "aggregateId", "pay-1", "orderId", ORDER, "amountMinorUnits", 5_000L,
                "currency", "INR", "fullRefund", false));
        awaitProjection("SELECT status FROM refund_projection WHERE refund_id = 'refund-" + ORDER + "'",
                "PROCESSED");
        assertThat(count("refund_projection")).isEqualTo(1);
    }

    @Test
    @DisplayName("the seller's report reads the projection over HTTP and reports the real figures")
    void reportReadsTheProjection() {
        publish(EventTypes.ORDER_PLACED, ORDER, Map.of(
                "orderId", ORDER, "customerId", "cust-1", "orderNumber", "ORD-1",
                "totalMinorUnits", 125_000L, "currency", "INR", "lineCount", 1));
        publish(EventTypes.ORDER_CONFIRMED, ORDER, Map.of(
                "orderId", ORDER, "customerId", "cust-1", "orderNumber", "ORD-1",
                "deliveryChargeMinorUnits", 0L, "currency", "INR"));
        awaitProjection("SELECT status FROM order_projection WHERE order_id = '" + ORDER + "'", "CONFIRMED");

        var response = get("/api/v1/reports/SALES?days=30", adminToken(UUID.randomUUID()));
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json(response);
        assertThat(body.get("report").asText()).isEqualTo("SALES");
        assertThat(metric(body, "confirmed").asLong()).isEqualTo(1);
        assertThat(metric(body, "revenueMinorUnits").asLong()).isEqualTo(125_000L);
    }

    @Test
    @DisplayName("an event this service does not project is simply ignored")
    void unknownEventIsIgnored() {
        publish(EventTypes.PRODUCT_CREATED, "prod-1", Map.of("productId", "prod-1"));
        // Nothing to assert but that the consumer did not blow up; a follow-up event must still work.
        publish(EventTypes.ORDER_PLACED, ORDER, Map.of(
                "orderId", ORDER, "customerId", "cust-1", "orderNumber", "ORD-1",
                "totalMinorUnits", 1L, "currency", "INR", "lineCount", 1));
        awaitProjection("SELECT status FROM order_projection WHERE order_id = '" + ORDER + "'", "PLACED");
    }

    private com.fasterxml.jackson.databind.JsonNode metric(com.fasterxml.jackson.databind.JsonNode body,
                                                           String key) {
        for (var node : body.get("metrics")) {
            if (node.get("key").asText().equals(key)) {
                return node.get("value");
            }
        }
        throw new AssertionError("no metric named " + key + " in " + body);
    }

    private void awaitProjection(String sql, String expected) {
        Awaitility.await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(query(sql)).isEqualTo(expected));
    }

    private String query(String sql) {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        } catch (Exception e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
    }

    private long count(String table) {
        return Long.parseLong(query("SELECT count(*) FROM " + table));
    }

    @Test
    @DisplayName("the dashboard is served to a report reader and refused to a customer")
    void dashboardAuthorization() {
        assertThat(get("/api/v1/dashboard", adminToken(UUID.randomUUID())).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/dashboard", customerToken(UUID.randomUUID())).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/dashboard", null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("the report window is echoed back and a caller-supplied window is honoured")
    void windowIsHonoured() {
        Instant from = Instant.parse("2020-01-01T00:00:00Z");
        Instant to = Instant.parse("2020-01-02T00:00:00Z");
        var response = get("/api/v1/reports/ORDERS?from=" + from + "&to=" + to,
                adminToken(UUID.randomUUID()));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("from").asText()).isEqualTo(from.toString());
        assertThat(json(response).get("to").asText()).isEqualTo(to.toString());
    }

    @Test
    @DisplayName("every report type is served without error")
    void everyReportTypeIsServed() {
        String token = adminToken(UUID.randomUUID());
        for (String type : List.of("SALES", "ORDERS", "PAYMENTS", "PRODUCTION", "DELIVERY", "REFUNDS",
                "CUSTOMERS")) {
            assertThat(get("/api/v1/reports/" + type + "?days=30", token).statusCode())
                    .as("report %s", type).isEqualTo(200);
        }
    }
}
