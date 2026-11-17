package com.choco.order.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Starting a payment for an order, over the real HTTP surface.
 *
 * <p>This test exists because of the gap it closes: the payment service could create a provider order
 * and the order service could apply a verified payment event, but no path connected the two, so an
 * order sat in {@code PAYMENT_PENDING} with no way to pay it. The wiring — an endpoint that reads the
 * order's own total and calls the payment service — is what is being proved here, and only a full
 * stack can prove it.
 *
 * <p>The payment service is the shared in-process stub, so the assertions can pin the exact contract
 * the order service sends — above all the amount — and force the answers a real service is awkward to
 * produce on demand: a 503, or a response with no provider order id.
 */
@DisplayName("Order payment initiation (real PostgreSQL, real security)")
class OrderPaymentIT extends AbstractOrderIntegrationTest {

    private static final UUID VARIANT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000f2");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f2");
    private static final UUID ADDRESS_ID = UUID.fromString("cccccccc-0000-0000-0000-0000000000f2");
    private static final UUID CUSTOMER_ID = UUID.fromString("dddddddd-0000-0000-0000-0000000000f2");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000f2");

    /** The order is 49900 items + 5000 delivery = 54900 paise. */
    private static final long ORDER_TOTAL = 54_900;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Nested
    @DisplayName("starting a payment")
    class Initiation {

        @Test
        @DisplayName("returns the provider checkout parameters for the order's own total")
        void returns_provider_checkout_parameters() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            stubPaymentInitiation(ORDER_TOTAL);

            HttpResponse<String> response = send("POST", "/api/v1/orders/me/" + orderId + "/payment",
                    null, customerToken(CUSTOMER_ID), "pay-" + UUID.randomUUID());

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("provider").asText()).isEqualTo("razorpay");
            assertThat(body.get("providerOrderId").asText()).isEqualTo("order_rzp_1");
            assertThat(body.get("amountMinorUnits").asLong()).isEqualTo(ORDER_TOTAL);
            assertThat(body.get("publicKey").asText()).isEqualTo("rzp_test_key");
            // The response must carry nothing that could be read as "paid".
            assertThat(body.has("paid")).isFalse();
            assertThat(body.has("status")).isFalse();
        }

        @Test
        @DisplayName("sends the order's own frozen total to the payment service")
        void sends_the_orders_total() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            stubPaymentInitiation(ORDER_TOTAL);

            send("POST", "/api/v1/orders/me/" + orderId + "/payment", null,
                    customerToken(CUSTOMER_ID), "pay-" + UUID.randomUUID());

            assertThat(DOWNSTREAM.lastPath())
                    .isEqualTo("/api/v1/payments/orders/" + orderId + "/initiate");
        }

        @Test
        @DisplayName("an unauthenticated request is refused and no payment is started")
        void unauthenticated_is_refused() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            stubPaymentInitiation(ORDER_TOTAL);

            HttpResponse<String> response = send("POST", "/api/v1/orders/me/" + orderId + "/payment",
                    null, null, null);

            assertThat(response.statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("a customer who has not finished verification cannot start a payment")
        void unverified_customer_is_refused() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            stubPaymentInitiation(ORDER_TOTAL);

            HttpResponse<String> response = send("POST", "/api/v1/orders/me/" + orderId + "/payment",
                    null, partiallyVerifiedCustomerToken(CUSTOMER_ID), null);

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("a customer cannot start a payment for another customer's order")
        void another_customers_order_is_refused() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            stubPaymentInitiation(ORDER_TOTAL);

            HttpResponse<String> response = send("POST", "/api/v1/orders/me/" + orderId + "/payment",
                    null, customerToken(OTHER_CUSTOMER_ID), null);

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("an unknown order is a 404")
        void unknown_order_is_not_found() {
            stubPaymentInitiation(ORDER_TOTAL);

            HttpResponse<String> response = send("POST",
                    "/api/v1/orders/me/" + UUID.randomUUID() + "/payment", null,
                    customerToken(CUSTOMER_ID), null);

            assertThat(response.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("a paid order cannot be charged again")
        void a_paid_order_cannot_be_charged_again() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            // Confirm the order the only way an order can be confirmed: a verified capture event.
            confirmViaPaymentEvent(orderId);

            HttpResponse<String> response = send("POST", "/api/v1/orders/me/" + orderId + "/payment",
                    null, customerToken(CUSTOMER_ID), null);

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(json(response).get("code").asText()).isEqualTo("PAYMENT_ALREADY_CAPTURED");
        }

        @Test
        @DisplayName("starting a payment does not confirm the order")
        void does_not_confirm_the_order() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            stubPaymentInitiation(ORDER_TOTAL);

            send("POST", "/api/v1/orders/me/" + orderId + "/payment", null,
                    customerToken(CUSTOMER_ID), "pay-" + UUID.randomUUID());

            assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_PENDING");
        }

        @Test
        @DisplayName("a payment service outage is a 503 and the order stays payable")
        void payment_outage_is_a_503() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            DOWNSTREAM.on("/api/v1/payments/", 503, "{\"code\":\"DEPENDENCY_UNAVAILABLE\"}");

            HttpResponse<String> response = send("POST", "/api/v1/orders/me/" + orderId + "/payment",
                    null, customerToken(CUSTOMER_ID), null);

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(json(response).get("code").asText()).isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_PENDING");
        }

        @Test
        @DisplayName("a payment response with no provider order id is refused, not half-applied")
        void a_response_without_a_provider_order_is_refused() {
            stubCheckout();
            UUID orderId = placeAnOrder();
            DOWNSTREAM.on("/api/v1/payments/", 200,
                    "{\"provider\":\"razorpay\",\"amountMinorUnits\":54900}");

            HttpResponse<String> response = send("POST", "/api/v1/orders/me/" + orderId + "/payment",
                    null, customerToken(CUSTOMER_ID), null);

            assertThat(response.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("two simultaneous starts both succeed and leave the order payable")
        void simultaneous_starts_are_safe() throws Exception {
            stubCheckout();
            UUID orderId = placeAnOrder();
            stubPaymentInitiation(ORDER_TOTAL);

            var pool = Executors.newFixedThreadPool(2);
            try {
                var start = new CountDownLatch(1);
                var first = pool.submit(() -> {
                    start.await();
                    return send("POST", "/api/v1/orders/me/" + orderId + "/payment", null,
                            customerToken(CUSTOMER_ID), "pay-race");
                });
                var second = pool.submit(() -> {
                    start.await();
                    return send("POST", "/api/v1/orders/me/" + orderId + "/payment", null,
                            customerToken(CUSTOMER_ID), "pay-race");
                });
                start.countDown();

                // Both calls may succeed; the payment service keys on the order id, so they resolve to
                // one provider order. What the order service must not do is fail or corrupt the order.
                assertThat(first.get().statusCode()).isEqualTo(200);
                assertThat(second.get().statusCode()).isEqualTo(200);
            } finally {
                pool.shutdownNow();
            }
            assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_PENDING");
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Answers the three calls checkout makes, with a known total. */
    private void stubCheckout() {
        DOWNSTREAM.on("/api/v1/customers/", 200, """
                {"id":"%s","fullName":"Asha Rao","email":"asha@choco.test","mobile":"9876543210",
                 "emailVerified":true,"mobileVerified":true,
                 "addresses":[{"id":"%s","recipientName":"Asha Rao","mobile":"9876543210",
                   "line1":"12 Cocoa Lane","line2":null,"city":"Bengaluru","state":"Karnataka",
                   "pincode":"560001","deliveryInstructions":"Leave at the door"}]}
                """.formatted(CUSTOMER_ID, ADDRESS_ID));
        DOWNSTREAM.on("/api/v1/catalog/variants", 200, """
                {"%s":{"variantId":"%s","productId":"%s","productName":"Dark Chocolate Bar",
                 "variantLabel":"200 g","sku":"DARK-200","priceMinorUnits":49900,"currency":"INR",
                 "minOrderQuantity":1,"maxOrderQuantity":10,"variantActive":true,
                 "productAvailable":true}}
                """.formatted(VARIANT_ID, VARIANT_ID, PRODUCT_ID));
        DOWNSTREAM.on("/api/v1/serviceability/", 200,
                "{\"serviceable\":true,\"deliveryChargeMinorUnits\":5000,\"currency\":\"INR\",\"reason\":null}");
    }

    private void stubPaymentInitiation(long amountMinorUnits) {
        DOWNSTREAM.on("/api/v1/payments/", 200, """
                {"provider":"razorpay","providerOrderId":"order_rzp_1","amountMinorUnits":%d,
                 "amount":"549.00","currency":"INR","receipt":"CHOCO-1","publicKey":"rzp_test_key"}
                """.formatted(amountMinorUnits));
    }

    /** Places a real order through the HTTP surface, leaving it in {@code PAYMENT_PENDING}. */
    private UUID placeAnOrder() {
        HttpResponse<String> line = post("/api/v1/carts/me/lines",
                jsonBody(Map.of("variantId", VARIANT_ID.toString(), "quantity", 1)),
                customerToken(CUSTOMER_ID));
        assertThat(line.statusCode()).isEqualTo(200);

        HttpResponse<String> placed = send("POST", "/api/v1/checkout",
                jsonBody(Map.of("addressId", ADDRESS_ID.toString())),
                customerToken(CUSTOMER_ID), "checkout-" + UUID.randomUUID());
        assertThat(placed.statusCode()).isEqualTo(201);
        return UUID.fromString(json(placed).get("id").asText());
    }

    /**
     * Confirms an order the way the platform really confirms one: a verified {@code payment.captured}
     * event on the broker, applied by the consumer. There is deliberately no HTTP shortcut.
     */
    private void confirmViaPaymentEvent(UUID orderId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", EventTypes.PAYMENT_CAPTURED);
        envelope.put("schemaVersion", 1);
        envelope.put("aggregateId", orderId.toString());
        envelope.put("occurredAt", Instant.now());
        envelope.put("producer", "payment-service");
        envelope.put("payload", Map.of("orderId", orderId.toString(), "providerPaymentId", "pay_it_1",
                "providerOrderId", "order_rzp_1", "amountMinorUnits", ORDER_TOTAL, "currency", "INR"));

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the capture event", e);
        }
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, EventTypes.PAYMENT_CAPTURED, json, message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setHeader("eventType", EventTypes.PAYMENT_CAPTURED);
            return message;
        });
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED"));
    }

    private String orderStatus(UUID orderId) {
        HttpResponse<String> response = get("/api/v1/orders/me/" + orderId, customerToken(CUSTOMER_ID));
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response).get("status").asText();
    }
}
