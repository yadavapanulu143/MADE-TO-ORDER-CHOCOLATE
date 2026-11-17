package com.choco.payment.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The payment service's public HTTP surface, exercised end to end.
 *
 * <p>Every request goes through the real filter chain, the real controllers, the real use cases, a
 * real PostgreSQL and the outbox. The provider is the shared stand-in, which computes real HMAC
 * signatures, so the security-critical paths — checkout signature verification and webhook signature
 * verification — are tested for real rather than assumed.
 *
 * <p>The point of these tests is the wiring and the boundary: that an unauthenticated caller cannot
 * reach a payment, that one customer cannot see or verify another's payment, that a forged signature
 * is refused, and that a repeated webhook does not double-count.
 */
@DisplayName("Payment HTTP journey (real PostgreSQL, real security)")
class PaymentJourneyIT extends AbstractPaymentIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("11111111-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
    private static final long AMOUNT = 49_900;

    /** Initiates a payment for an order on behalf of the customer. */
    private JsonNode initiate(UUID customerId, UUID orderId, String idempotencyKey) {
        HttpResponse<String> response = post("/api/v1/payments/orders/" + orderId + "/initiate",
                jsonBody(Map.of("amountMinorUnits", AMOUNT, "currency", "INR", "receipt", "rcpt-1")),
                customerToken(customerId), idempotencyKey);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    @Nested
    @DisplayName("initiation")
    class Initiation {

        @Test
        @DisplayName("an unauthenticated request is refused")
        void unauthenticated_is_refused() {
            HttpResponse<String> response = post("/api/v1/payments/orders/" + UUID.randomUUID()
                    + "/initiate", jsonBody(Map.of("amountMinorUnits", AMOUNT, "currency", "INR")), null);
            assertThat(response.statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("a customer starts a payment and receives the publishable key and provider order id")
        void customer_starts_a_payment() {
            JsonNode body = initiate(CUSTOMER_ID, UUID.randomUUID(), null);

            assertThat(body.get("provider").asText()).isEqualTo("razorpay");
            assertThat(body.get("providerOrderId").asText()).startsWith("order_fake_");
            assertThat(body.get("amountMinorUnits").asLong()).isEqualTo(AMOUNT);
            assertThat(body.get("publicKey").asText()).isEqualTo(PUBLISHABLE_KEY);
        }

        @Test
        @DisplayName("initiation is idempotent per order: a retry reuses the same provider order")
        void initiation_is_idempotent_per_order() {
            UUID orderId = UUID.randomUUID();
            String first = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();

            String second = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();

            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("a replayed idempotency key for a different order is a 409")
        void replayed_key_for_different_order_is_conflict() {
            initiate(CUSTOMER_ID, UUID.randomUUID(), "idem-key-1");

            HttpResponse<String> response = post("/api/v1/payments/orders/" + UUID.randomUUID() + "/initiate",
                    jsonBody(Map.of("amountMinorUnits", AMOUNT, "currency", "INR")),
                    customerToken(CUSTOMER_ID), "idem-key-1");

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(json(response).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        }

        @Test
        @DisplayName("a non-positive amount is rejected at the boundary")
        void non_positive_amount_rejected() {
            HttpResponse<String> response = post("/api/v1/payments/orders/" + UUID.randomUUID() + "/initiate",
                    jsonBody(Map.of("amountMinorUnits", 0, "currency", "INR")),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("verification")
    class Verification {

        private Map<String, Object> verifyBody(String providerOrderId, String providerPaymentId,
                                               String signature) {
            return Map.of("providerOrderId", providerOrderId, "providerPaymentId", providerPaymentId,
                    "signature", signature);
        }

        @Test
        @DisplayName("a captured payment with a valid signature is recorded as paid")
        void captured_with_valid_signature_is_paid() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();
            PROVIDER.report("pay_1", providerOrderId, AMOUNT, "captured", "upi");

            HttpResponse<String> response = post("/api/v1/payments/me/orders/" + orderId + "/verify",
                    jsonBody(verifyBody(providerOrderId, "pay_1", signCheckout(providerOrderId, "pay_1"))),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("status").asText()).isEqualTo("PAID");
            assertThat(body.get("providerPaymentId").asText()).isEqualTo("pay_1");
        }

        @Test
        @DisplayName("a forged signature is refused and the payment is not marked paid")
        void forged_signature_refused() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();

            HttpResponse<String> response = post("/api/v1/payments/me/orders/" + orderId + "/verify",
                    jsonBody(verifyBody(providerOrderId, "pay_1", "not-a-real-signature")),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json(response).get("code").asText()).isEqualTo("PAYMENT_SIGNATURE_INVALID");

            // The payment must not be paid.
            HttpResponse<String> read = get("/api/v1/payments/me/orders/" + orderId,
                    customerToken(CUSTOMER_ID));
            assertThat(json(read).get("status").asText()).isNotEqualTo("PAID");
        }

        @Test
        @DisplayName("a customer cannot verify another customer's payment")
        void cannot_verify_anothers_payment() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();

            HttpResponse<String> response = post("/api/v1/payments/me/orders/" + orderId + "/verify",
                    jsonBody(verifyBody(providerOrderId, "pay_1", signCheckout(providerOrderId, "pay_1"))),
                    customerToken(OTHER_CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("an authorised-but-not-captured payment is recorded as pending, not paid")
        void authorised_is_pending() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();
            PROVIDER.report("pay_1", providerOrderId, AMOUNT, "authorized", "upi");

            HttpResponse<String> response = post("/api/v1/payments/me/orders/" + orderId + "/verify",
                    jsonBody(verifyBody(providerOrderId, "pay_1", signCheckout(providerOrderId, "pay_1"))),
                    customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("status").asText()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("webhooks")
    class Webhooks {

        private String captureBody(String providerOrderId, String providerPaymentId) {
            return """
                    {"event":"payment.captured","payload":{"payment":{"entity":{
                      "id":"%s","order_id":"%s","amount":%d,"currency":"INR","method":"upi",
                      "status":"captured"}}}}
                    """.formatted(providerPaymentId, providerOrderId, AMOUNT);
        }

        @Test
        @DisplayName("a signed capture webhook marks the payment paid")
        void signed_capture_webhook_marks_paid() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();

            HttpResponse<String> response = postWebhook(captureBody(providerOrderId, "pay_1"));

            assertThat(response.statusCode()).isEqualTo(200);
            HttpResponse<String> read = get("/api/v1/payments/me/orders/" + orderId,
                    customerToken(CUSTOMER_ID));
            assertThat(json(read).get("status").asText()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("an unsigned webhook is refused")
        void unsigned_webhook_refused() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();

            HttpResponse<String> response = postWebhook(captureBody(providerOrderId, "pay_1"), "forged");

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json(response).get("code").asText()).isEqualTo("PAYMENT_SIGNATURE_INVALID");
        }

        @Test
        @DisplayName("a duplicate capture webhook changes nothing the second time")
        void duplicate_capture_webhook_is_idempotent() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();

            assertThat(postWebhook(captureBody(providerOrderId, "pay_1")).statusCode()).isEqualTo(200);
            assertThat(postWebhook(captureBody(providerOrderId, "pay_1")).statusCode()).isEqualTo(200);

            HttpResponse<String> read = get("/api/v1/payments/me/orders/" + orderId,
                    customerToken(CUSTOMER_ID));
            assertThat(json(read).get("status").asText()).isEqualTo("PAID");
            // One attempt row: the duplicate delivery did not add a second capture.
            assertThat(json(read).get("attempts")).hasSize(1);
        }

        @Test
        @DisplayName("a webhook for an unknown payment is acknowledged, not retried forever")
        void unknown_payment_webhook_acknowledged() {
            HttpResponse<String> response = postWebhook(captureBody("order_unknown", "pay_unknown"));

            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("customer reads")
    class CustomerReads {

        @Test
        @DisplayName("a customer reads only their own payment history")
        void history_is_scoped_to_the_owner() {
            initiate(CUSTOMER_ID, UUID.randomUUID(), null);
            initiate(OTHER_CUSTOMER_ID, UUID.randomUUID(), null);

            HttpResponse<String> response = get("/api/v1/payments/me", customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("totalElements").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("a customer cannot read another customer's payment by id")
        void cannot_read_anothers_payment() {
            UUID orderId = UUID.randomUUID();
            String paymentId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();
            // Resolve the local payment id through the customer's own view.
            String localId = json(get("/api/v1/payments/me/orders/" + orderId,
                    customerToken(CUSTOMER_ID))).get("id").asText();
            assertThat(paymentId).isNotBlank();

            HttpResponse<String> response = get("/api/v1/payments/me/" + localId,
                    customerToken(OTHER_CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("seller surface")
    class SellerSurface {

        @Test
        @DisplayName("a customer token cannot reach the seller's payment list")
        void customer_cannot_reach_admin() {
            HttpResponse<String> response = get("/api/v1/admin/payments", customerToken(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("an administrator sees the payment counters")
        void admin_sees_counters() {
            initiate(CUSTOMER_ID, UUID.randomUUID(), null);

            HttpResponse<String> response = get("/api/v1/admin/payments/counters", adminToken(ADMIN_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("total").asLong()).isEqualTo(1);
            assertThat(body.get("paid").asLong()).isZero();
        }

        @Test
        @DisplayName("an administrator refunds a captured payment")
        void admin_refunds_a_payment() {
            UUID orderId = UUID.randomUUID();
            String providerOrderId = initiate(CUSTOMER_ID, orderId, null).get("providerOrderId").asText();
            PROVIDER.report("pay_1", providerOrderId, AMOUNT, "captured", "upi");
            post("/api/v1/payments/me/orders/" + orderId + "/verify",
                    jsonBody(Map.of("providerOrderId", providerOrderId, "providerPaymentId", "pay_1",
                            "signature", signCheckout(providerOrderId, "pay_1"))),
                    customerToken(CUSTOMER_ID));
            String paymentId = json(get("/api/v1/payments/me/orders/" + orderId,
                    customerToken(CUSTOMER_ID))).get("id").asText();

            HttpResponse<String> response = post("/api/v1/admin/payments/" + paymentId + "/refunds",
                    jsonBody(Map.of("amountMinorUnits", AMOUNT, "reason", "customer request")),
                    adminToken(ADMIN_ID));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("status").asText()).isEqualTo("PROCESSED");

            HttpResponse<String> read = get("/api/v1/admin/payments/" + paymentId, adminToken(ADMIN_ID));
            assertThat(json(read).get("status").asText()).isEqualTo("REFUNDED");
        }
    }
}
