package com.choco.payment.adapter.out.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.payment.domain.port.PaymentGatewayPort.WebhookEvent;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Razorpay adapter's own logic: the provider's signature algorithms and the webhook body
 * parsing.
 *
 * <p>The signature checks are the security boundary of the whole payment flow, so they are tested with
 * signatures this test computes independently with {@code javax.crypto}, rather than by calling the
 * SDK to generate them. If the adapter ever verified the wrong string — the wrong field order, the
 * parsed JSON instead of the raw body — a test that reused the SDK's own signer would still pass, and
 * the bug would reach production.
 *
 * <p>The gateway is constructed with a null client because none of the methods under test touch the
 * network: {@code verifyCheckoutSignature}, {@code verifyWebhookSignature} and {@code parseWebhook}
 * are pure.
 */
@DisplayName("Razorpay payment gateway")
class RazorpayPaymentGatewayTest {

    private static final String KEY_SECRET = "test_key_secret";
    private static final String WEBHOOK_SECRET = "test_webhook_secret";

    private RazorpayPaymentGateway gateway(String keySecret, String webhookSecret) {
        RazorpayProperties properties = new RazorpayProperties();
        properties.setKeyId("rzp_test_abc123");
        properties.setKeySecret(keySecret);
        properties.setWebhookSecret(webhookSecret);
        return new RazorpayPaymentGateway(null, properties);
    }

    private static String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("could not compute an HMAC for the test", e);
        }
    }

    @Test
    @DisplayName("accepts a checkout signature computed over order id and payment id")
    void acceptsValidCheckoutSignature() {
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        String signature = hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET);

        assertThat(gateway(KEY_SECRET, WEBHOOK_SECRET)
                .verifyCheckoutSignature(orderId, paymentId, signature)).isTrue();
    }

    @Test
    @DisplayName("rejects a checkout signature computed with the wrong secret")
    void rejectsCheckoutSignatureFromWrongSecret() {
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        String forged = hmacSha256Hex(orderId + "|" + paymentId, "not-the-secret");

        assertThat(gateway(KEY_SECRET, WEBHOOK_SECRET)
                .verifyCheckoutSignature(orderId, paymentId, forged)).isFalse();
    }

    @Test
    @DisplayName("rejects a checkout signature over different identifiers")
    void rejectsCheckoutSignatureForDifferentIds() {
        String signature = hmacSha256Hex("order_OTHER|pay_OTHER", KEY_SECRET);

        assertThat(gateway(KEY_SECRET, WEBHOOK_SECRET)
                .verifyCheckoutSignature("order_ABC123", "pay_XYZ789", signature)).isFalse();
    }

    @Test
    @DisplayName("rejects a blank checkout signature without calling the provider")
    void rejectsBlankCheckoutSignature() {
        assertThat(gateway(KEY_SECRET, WEBHOOK_SECRET)
                .verifyCheckoutSignature("order_1", "pay_1", "  ")).isFalse();
    }

    @Test
    @DisplayName("accepts a webhook signature computed over the raw body")
    void acceptsValidWebhookSignature() {
        String body = "{\"event\":\"payment.captured\"}";
        String signature = hmacSha256Hex(body, WEBHOOK_SECRET);

        assertThat(gateway(KEY_SECRET, WEBHOOK_SECRET).verifyWebhookSignature(body, signature)).isTrue();
    }

    @Test
    @DisplayName("rejects a webhook signature when the body was altered")
    void rejectsTamperedWebhookBody() {
        String signature = hmacSha256Hex("{\"event\":\"payment.captured\"}", WEBHOOK_SECRET);

        assertThat(gateway(KEY_SECRET, WEBHOOK_SECRET)
                .verifyWebhookSignature("{\"event\":\"payment.failed\"}", signature)).isFalse();
    }

    @Test
    @DisplayName("refuses every webhook when no webhook secret is configured")
    void refusesWebhookWithoutSecret() {
        String body = "{\"event\":\"payment.captured\"}";

        assertThat(gateway(KEY_SECRET, "").verifyWebhookSignature(body, "any-signature")).isFalse();
        assertThat(gateway(KEY_SECRET, null).verifyWebhookSignature(body, "any-signature")).isFalse();
    }

    @Test
    @DisplayName("parses a captured payment webhook")
    void parsesCapturedPayment() {
        String body = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_ABC",
                        "order_id": "order_XYZ",
                        "amount": 49900,
                        "currency": "INR",
                        "method": "upi",
                        "status": "captured"
                      }
                    }
                  }
                }
                """;

        WebhookEvent event = gateway(KEY_SECRET, WEBHOOK_SECRET).parseWebhook(body);

        assertThat(event.eventType()).isEqualTo("payment.captured");
        assertThat(event.providerPaymentId()).isEqualTo("pay_ABC");
        assertThat(event.providerOrderId()).isEqualTo("order_XYZ");
        assertThat(event.amountMinorUnits()).isEqualTo(49900L);
        assertThat(event.currency()).isEqualTo("INR");
        assertThat(event.method()).isEqualTo("upi");
    }

    @Test
    @DisplayName("parses a failed payment webhook with its reason")
    void parsesFailedPayment() {
        String body = """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_ABC",
                        "order_id": "order_XYZ",
                        "amount": 49900,
                        "currency": "INR",
                        "error_description": "Payment declined by the bank"
                      }
                    }
                  }
                }
                """;

        WebhookEvent event = gateway(KEY_SECRET, WEBHOOK_SECRET).parseWebhook(body);

        assertThat(event.eventType()).isEqualTo("payment.failed");
        assertThat(event.failureReason()).isEqualTo("Payment declined by the bank");
    }

    @Test
    @DisplayName("parses a refund webhook, taking the payment id from the refund when absent")
    void parsesRefundWebhook() {
        String body = """
                {
                  "event": "refund.processed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "rfnd_123",
                        "payment_id": "pay_ABC",
                        "amount": 20000,
                        "currency": "INR",
                        "status": "processed"
                      }
                    }
                  }
                }
                """;

        WebhookEvent event = gateway(KEY_SECRET, WEBHOOK_SECRET).parseWebhook(body);

        assertThat(event.eventType()).isEqualTo("refund.processed");
        assertThat(event.providerRefundId()).isEqualTo("rfnd_123");
        assertThat(event.providerPaymentId()).isEqualTo("pay_ABC");
        assertThat(event.amountMinorUnits()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("exposes the publishable key id, never the secret")
    void exposesOnlyPublishableKey() {
        RazorpayPaymentGateway gateway = gateway(KEY_SECRET, WEBHOOK_SECRET);

        assertThat(gateway.publishableKey()).isEqualTo("rzp_test_abc123");
        assertThat(gateway.publishableKey()).doesNotContain(KEY_SECRET);
    }
}
