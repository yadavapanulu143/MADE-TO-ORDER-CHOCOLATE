package com.choco.order.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The order service's public HTTP surface, exercised end to end.
 *
 * <p>Every request goes through the real filter chain, the real controllers, the real use cases and a
 * real PostgreSQL. The downstream services are the shared in-process stub, so the assertions can pin
 * the exact contract the order service sends to the catalogue, identity and delivery services — and
 * can force the awkward answers, a 404 or a 503, that are hard to make a real service produce.
 *
 * <p>The point of these tests is the wiring: a controller that forgets the ownership check, a route
 * that is reachable without a token, or a status the aggregate should have refused. The unit tests
 * already prove the rules; this proves the rules are actually reachable and enforced over the wire.
 */
@DisplayName("Order HTTP journey (real PostgreSQL, real security)")
class OrderJourneyIT extends AbstractOrderIntegrationTest {

    private static final UUID VARIANT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID ADDRESS_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

    /** Answers the three downstream calls checkout makes: profile, catalogue lookup, serviceability. */
    private void stubHappyDownstream(int unitPriceMinorUnits, int minQty, int maxQty,
                                     long deliveryChargeMinorUnits, boolean serviceable) {
        DOWNSTREAM.on("/api/v1/customers/", 200, """
                {"id":"%s","fullName":"Asha Rao","email":"asha@choco.test","mobile":"9876543210",
                 "emailVerified":true,"mobileVerified":true,
                 "addresses":[{"id":"%s","recipientName":"Asha Rao","mobile":"9876543210",
                   "line1":"12 Cocoa Lane","line2":null,"city":"Bengaluru","state":"Karnataka",
                   "pincode":"560001","deliveryInstructions":"Leave at the door"}]}
                """.formatted(CUSTOMER_ID, ADDRESS_ID));
        DOWNSTREAM.on("/api/v1/catalog/variants", 200, """
                {"%s":{"variantId":"%s","productId":"%s","productName":"Dark Chocolate Bar",
                 "variantLabel":"200 g","sku":"DARK-200","priceMinorUnits":%d,"currency":"INR",
                 "minOrderQuantity":%d,"maxOrderQuantity":%d,"variantActive":true,
                 "productAvailable":true}}
                """.formatted(VARIANT_ID, VARIANT_ID, PRODUCT_ID, unitPriceMinorUnits, minQty, maxQty));
        DOWNSTREAM.on("/api/v1/serviceability/", 200, """
                {"serviceable":%s,"deliveryChargeMinorUnits":%d,"currency":"INR","reason":null}
                """.formatted(serviceable, deliveryChargeMinorUnits));
    }

    private static final UUID CUSTOMER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    @org.springframework.beans.factory.annotation.Autowired
    private com.choco.order.application.service.OrderCommandService orderCommandService;

    @Nested
    @DisplayName("cart")
    class Cart {

        @Test
        @DisplayName("an unauthenticated request is refused")
        void unauthenticated_is_refused() {
            HttpResponse<String> response = get("/api/v1/carts/me", null);
            assertThat(response.statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("adding a line reads the catalogue and stores the display price")
        void adding_a_line_reads_the_catalogue() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);

            HttpResponse<String> response = post("/api/v1/carts/me/lines",
                    jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", 3)),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("totalItems").asInt()).isEqualTo(3);
            assertThat(body.get("lines")).hasSize(1);
            assertThat(body.get("lines").get(0).get("unitPriceMinorUnits").asLong()).isEqualTo(49_900);
            assertThat(body.get("estimatedTotalMinorUnits").asLong()).isEqualTo(149_700);
            assertThat(DOWNSTREAM.lastPath()).isEqualTo("/api/v1/catalog/variants");
        }

        @Test
        @DisplayName("an unknown variant is a 404, not a silently empty cart")
        void unknown_variant_is_not_found() {
            DOWNSTREAM.on("/api/v1/catalog/variants", 200, "{}");

            HttpResponse<String> response = post("/api/v1/carts/me/lines",
                    jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", 1)),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(json(response).get("code").asText()).isEqualTo("NOT_FOUND");
        }

        @Test
        @DisplayName("a withdrawn variant is a 409 with the catalogue's availability code")
        void withdrawn_variant_is_conflict() {
            DOWNSTREAM.on("/api/v1/catalog/variants", 200, """
                    {"%s":{"variantId":"%s","productId":"%s","productName":"Dark Chocolate Bar",
                     "variantLabel":"200 g","sku":"DARK-200","priceMinorUnits":49900,"currency":"INR",
                     "minOrderQuantity":1,"maxOrderQuantity":10,"variantActive":true,
                     "productAvailable":false}}
                    """.formatted(VARIANT_ID, VARIANT_ID, PRODUCT_ID));

            HttpResponse<String> response = post("/api/v1/carts/me/lines",
                    jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", 1)),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(json(response).get("code").asText()).isEqualTo("PRODUCT_UNAVAILABLE");
        }

        @Test
        @DisplayName("a zero quantity is rejected at the boundary")
        void zero_quantity_is_rejected() {
            HttpResponse<String> response = post("/api/v1/carts/me/lines",
                    jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", 0)),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json(response).get("code").asText()).isEqualTo("VALIDATION_FAILED");
        }

        @Test
        @DisplayName("clearing the cart leaves it empty")
        void clearing_the_cart() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);
            post("/api/v1/carts/me/lines",
                    jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", 2)),
                    customerToken(CUSTOMER_ID));

            HttpResponse<String> response = send("DELETE", "/api/v1/carts/me", null,
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("lines")).isEmpty();
        }
    }

    @Nested
    @DisplayName("checkout")
    class Checkout {

        @Test
        @DisplayName("a quote prices the cart and the delivery charge without placing anything")
        void quote_prices_without_placing() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);
            addOneLine(2);

            HttpResponse<String> response = get("/api/v1/checkout/quote?addressId=" + ADDRESS_ID,
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("serviceable").asBoolean()).isTrue();
            assertThat(body.get("itemsTotalMinorUnits").asLong()).isEqualTo(99_800);
            assertThat(body.get("deliveryChargeMinorUnits").asLong()).isEqualTo(5_000);
            assertThat(body.get("totalMinorUnits").asLong()).isEqualTo(104_800);
        }

        @Test
        @DisplayName("a non-serviceable PIN code refuses the quote")
        void quote_refuses_unserviceable_pincode() {
            stubHappyDownstream(49_900, 1, 10, 0, false);
            addOneLine(1);

            HttpResponse<String> response = get("/api/v1/checkout/quote?addressId=" + ADDRESS_ID,
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("serviceable").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("placing an order snapshots the price and returns 201 with a Location")
        void placing_an_order_snapshots_the_price() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);
            addOneLine(2);

            HttpResponse<String> response = send("POST", "/api/v1/checkout",
                    jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                    customerToken(CUSTOMER_ID), "checkout-key-1");

            assertThat(response.statusCode()).isEqualTo(201);
            JsonNode body = json(response);
            assertThat(body.get("status").asText()).isEqualTo("PAYMENT_PENDING");
            assertThat(body.get("totalMinorUnits").asLong()).isEqualTo(104_800);
            assertThat(body.get("lines").get(0).get("unitPriceMinorUnits").asLong()).isEqualTo(49_900);
            assertThat(body.get("deliveryAddress").get("pincode").asText()).isEqualTo("560001");
            assertThat(response.headers().firstValue("Location")).isPresent();
        }

        @Test
        @DisplayName("the cart is emptied by a successful placement")
        void placing_clears_the_cart() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);
            addOneLine(1);

            send("POST", "/api/v1/checkout", jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                    customerToken(CUSTOMER_ID), "checkout-key-2");

            HttpResponse<String> cart = get("/api/v1/carts/me", customerToken(CUSTOMER_ID));
            assertThat(json(cart).get("lines")).isEmpty();
        }

        @Test
        @DisplayName("replaying an idempotency key returns the first order, not a second")
        void idempotent_placement_returns_the_first_order() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);
            addOneLine(1);

            HttpResponse<String> first = send("POST", "/api/v1/checkout",
                    jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                    customerToken(CUSTOMER_ID), "checkout-key-3");
            // A replay after the cart is empty must still succeed with the original order.
            HttpResponse<String> replay = send("POST", "/api/v1/checkout",
                    jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                    customerToken(CUSTOMER_ID), "checkout-key-3");

            assertThat(replay.statusCode()).isEqualTo(201);
            assertThat(json(replay).get("id").asText())
                    .isEqualTo(json(first).get("id").asText());
            assertThat(countOrders()).isEqualTo(1);
        }

        @Test
        @DisplayName("reusing a key for a different address is a conflict")
        void reusing_a_key_for_a_different_address_conflicts() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);
            addOneLine(1);
            send("POST", "/api/v1/checkout", jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                    customerToken(CUSTOMER_ID), "checkout-key-4");
            addOneLine(1);

            HttpResponse<String> replay = send("POST", "/api/v1/checkout",
                    jsonBody(Map.of("addressId", UUID.randomUUID().toString())),
                    customerToken(CUSTOMER_ID), "checkout-key-4");

            assertThat(replay.statusCode()).isEqualTo(409);
            assertThat(json(replay).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        }

        @Test
        @DisplayName("an unverified customer cannot place an order")
        void unverified_customer_cannot_place() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);
            addOneLine(1);

            HttpResponse<String> response = send("POST", "/api/v1/checkout",
                    jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                    partiallyVerifiedCustomerToken(CUSTOMER_ID), "checkout-key-5");

            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(json(response).get("code").asText()).isEqualTo("MOBILE_NOT_VERIFIED");
        }

        @Test
        @DisplayName("an empty cart cannot be checked out")
        void empty_cart_cannot_be_checked_out() {
            stubHappyDownstream(49_900, 1, 10, 5_000, true);

            HttpResponse<String> response = send("POST", "/api/v1/checkout",
                    jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                    customerToken(CUSTOMER_ID), "checkout-key-6");

            assertThat(response.statusCode()).isEqualTo(409);
        }
    }

    @Nested
    @DisplayName("customer orders")
    class CustomerOrders {

        @Test
        @DisplayName("a customer reads only their own order")
        void a_customer_reads_only_their_own_order() {
            UUID orderId = placeAnOrder();

            HttpResponse<String> own = get("/api/v1/orders/me/" + orderId, customerToken(CUSTOMER_ID));
            assertThat(own.statusCode()).isEqualTo(200);

            HttpResponse<String> other = get("/api/v1/orders/me/" + orderId,
                    customerToken(UUID.randomUUID()));
            assertThat(other.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("order history contains the placed order")
        void history_contains_the_placed_order() {
            placeAnOrder();

            HttpResponse<String> history = get("/api/v1/orders/me", customerToken(CUSTOMER_ID));
            assertThat(history.statusCode()).isEqualTo(200);
            assertThat(json(history).get("content")).hasSize(1);
            assertThat(json(history).get("content").get(0).get("status").asText())
                    .isEqualTo("PAYMENT_PENDING");
        }

        @Test
        @DisplayName("a customer can cancel an unpaid order")
        void a_customer_can_cancel_an_unpaid_order() {
            UUID orderId = placeAnOrder();

            HttpResponse<String> response = post("/api/v1/orders/me/" + orderId + "/cancel",
                    jsonBody(Map.of("reason", "Ordered by mistake")), customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("status").asText()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("a refund request on a paid order is recorded, not auto-decided")
        void refund_request_is_recorded() {
            UUID orderId = placeAnOrder();
            confirmPayment(orderId);

            HttpResponse<String> response = post("/api/v1/orders/me/" + orderId + "/refund-request",
                    jsonBody(Map.of("reason", "Changed my mind")), customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("status").asText()).isEqualTo("REFUND_UNDER_REVIEW");
        }

        @Test
        @DisplayName("a refund cannot be requested for an order that was never paid")
        void refund_needs_a_paid_order() {
            UUID orderId = placeAnOrder();

            HttpResponse<String> response = post("/api/v1/orders/me/" + orderId + "/refund-request",
                    jsonBody(Map.of("reason", "Changed my mind")), customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(409);
        }

        @Test
        @DisplayName("cancelling without a reason is rejected")
        void cancelling_without_a_reason_is_rejected() {
            UUID orderId = placeAnOrder();

            HttpResponse<String> response = post("/api/v1/orders/me/" + orderId + "/cancel",
                    jsonBody(Map.of("reason", "  ")), customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("admin lifecycle")
    class AdminLifecycle {

        @Test
        @DisplayName("a customer token cannot reach the admin surface")
        void a_customer_cannot_reach_the_admin_surface() {
            HttpResponse<String> response = get("/api/v1/admin/orders", customerToken(CUSTOMER_ID));
            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("the seller searches orders and reads the counters")
        void the_seller_searches_and_reads_counters() {
            placeAnOrder();

            HttpResponse<String> search = get("/api/v1/admin/orders", adminToken(ADMIN_ID));
            assertThat(search.statusCode()).isEqualTo(200);
            assertThat(json(search).get("content")).hasSize(1);

            HttpResponse<String> counters = get("/api/v1/admin/orders/counters", adminToken(ADMIN_ID));
            assertThat(counters.statusCode()).isEqualTo(200);
            assertThat(json(counters).get("paymentPending").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("an unknown status filter is rejected rather than ignored")
        void an_unknown_status_filter_is_rejected() {
            HttpResponse<String> response = get("/api/v1/admin/orders?status=NOT_A_STATUS",
                    adminToken(ADMIN_ID));
            assertThat(response.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("preparation cannot start before the payment is verified")
        void preparation_cannot_start_before_payment() {
            UUID orderId = placeAnOrder();

            HttpResponse<String> response = post("/api/v1/admin/orders/" + orderId + "/preparation",
                    jsonBody(Map.of("action", "START")), adminToken(ADMIN_ID));

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(json(response).get("code").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
        }

        @Test
        @DisplayName("a verified payment unlocks preparation, packaging and dispatch")
        void a_verified_payment_unlocks_the_workflow() {
            UUID orderId = placeAnOrder();
            confirmPayment(orderId);

            assertThat(preparation(orderId, "START").get("status").asText())
                    .isEqualTo("PREPARATION_STARTED");
            assertThat(preparation(orderId, "COMPLETE").get("status").asText())
                    .isEqualTo("PREPARATION_COMPLETED");
            assertThat(preparation(orderId, "PACKAGING").get("status").asText())
                    .isEqualTo("PACKAGING");
            assertThat(preparation(orderId, "READY_FOR_DISPATCH").get("status").asText())
                    .isEqualTo("READY_FOR_DISPATCH");
        }

        @Test
        @DisplayName("delivery is assigned, dispatched and delivered, with a status history")
        void delivery_workflow_is_recorded() {
            UUID orderId = placeAnOrder();
            confirmPayment(orderId);
            preparation(orderId, "START");
            preparation(orderId, "COMPLETE");
            preparation(orderId, "PACKAGING");
            preparation(orderId, "READY_FOR_DISPATCH");

            HttpResponse<String> assigned = post("/api/v1/admin/orders/" + orderId + "/delivery/assign",
                    jsonBody(Map.of("deliveryPersonName", "Ravi", "deliveryPersonPhone", "9000000000")),
                    adminToken(ADMIN_ID));
            assertThat(assigned.statusCode()).isEqualTo(200);
            assertThat(json(assigned).get("deliveryPersonName").asText()).isEqualTo("Ravi");
            assertThat(json(assigned).get("status").asText()).isEqualTo("READY_FOR_DISPATCH");
            assertThat(json(assigned).get("deliveryStatus").asText()).isEqualTo("ASSIGNED");

            HttpResponse<String> out = post("/api/v1/admin/orders/" + orderId + "/delivery",
                    jsonBody(Map.of("action", "OUT_FOR_DELIVERY")), adminToken(ADMIN_ID));
            assertThat(json(out).get("status").asText()).isEqualTo("OUT_FOR_DELIVERY");

            HttpResponse<String> delivered = post("/api/v1/admin/orders/" + orderId + "/delivery",
                    jsonBody(Map.of("action", "DELIVERED")), adminToken(ADMIN_ID));
            JsonNode body = json(delivered);
            assertThat(body.get("status").asText()).isEqualTo("DELIVERED");
            assertThat(body.get("deliveredAt").isNull()).isFalse();
            assertThat(body.get("statusHistory").size()).isGreaterThan(3);
        }

        @Test
        @DisplayName("a seller note is attached to the order")
        void a_seller_note_is_attached() {
            UUID orderId = placeAnOrder();

            HttpResponse<String> response = post("/api/v1/admin/orders/" + orderId + "/notes",
                    jsonBody(Map.of("text", "Customer asked for extra cocoa")), adminToken(ADMIN_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("notes")).hasSize(1);
            assertThat(json(response).get("notes").get(0).get("text").asText())
                    .isEqualTo("Customer asked for extra cocoa");
        }
    }

    // ---------------------------------------------------------------------------------------------

    private void addOneLine(int quantity) {
        HttpResponse<String> response = post("/api/v1/carts/me/lines",
                jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", quantity)),
                customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private UUID placeAnOrder() {
        stubHappyDownstream(49_900, 1, 10, 5_000, true);
        addOneLine(1);
        HttpResponse<String> response = send("POST", "/api/v1/checkout",
                jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                customerToken(CUSTOMER_ID), "order-" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json(response).get("id").asText());
    }

    /**
     * Confirms payment the only way the service allows: by calling the use case the payment service
     * would drive. There is deliberately no HTTP endpoint that marks an order paid — a browser must
     * never be able to confirm its own payment — so the test reaches the application service directly.
     */
    private void confirmPayment(UUID orderId) {
        orderCommandService.markPaid(orderId, "pay_test_" + orderId, "order_test_" + orderId);
    }

    private JsonNode preparation(UUID orderId, String action) {
        HttpResponse<String> response = post("/api/v1/admin/orders/" + orderId + "/preparation",
                jsonBody(Map.of("action", action)), adminToken(ADMIN_ID));
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    private long countOrders() {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM orders")) {
            rows.next();
            return rows.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException("could not count orders", e);
        }
    }
}
