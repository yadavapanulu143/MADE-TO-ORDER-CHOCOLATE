package com.choco.payment.adapter.out.gateway;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.domain.port.PaymentGatewayPort;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Utils;
import java.util.Map;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Razorpay implementation of {@link PaymentGatewayPort}.
 *
 * <p>The SDK is confined to this class. Nothing outside it imports a Razorpay type, so the business
 * rules stay independent of the provider and can be tested against a fake gateway.
 *
 * <p>Two verification paths, both required by the requirements and both delegated to the provider's own
 * crypto rather than reimplemented here:
 * <ul>
 *   <li>the checkout signature, checked over {@code order_id|payment_id} with the account secret;
 *   <li>the webhook signature, checked over the raw body with the webhook secret.
 * </ul>
 * Both use a constant-time comparison inside the SDK, so a timing side channel cannot be used to forge
 * a signature byte by byte.
 *
 * <p>No method here accepts or returns a card number, a CVV, a UPI PIN or a bank credential. The
 * provider's own checkout collects those; this service only ever sees identifiers and a coarse method.
 */
public class RazorpayPaymentGateway implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final RazorpayClient client;
    private final RazorpayProperties properties;

    public RazorpayPaymentGateway(RazorpayClient client, RazorpayProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public ProviderOrder createOrder(Money amount, String receipt, Map<String, String> notes) {
        JSONObject request = new JSONObject();
        request.put("amount", amount.minorUnits());
        request.put("currency", amount.currency().getCurrencyCode());
        if (receipt != null && !receipt.isBlank()) {
            request.put("receipt", receipt);
        }
        if (notes != null && !notes.isEmpty()) {
            request.put("notes", new JSONObject(notes));
        }
        try {
            Order order = client.orders.create(request);
            return new ProviderOrder(
                    order.get("id"),
                    asLong(order.get("amount")),
                    order.get("currency"),
                    order.get("status"));
        } catch (RazorpayException ex) {
            throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "The payment provider could not create an order: " + safeMessage(ex));
        }
    }

    @Override
    public ProviderPayment fetchPayment(String providerPaymentId) {
        try {
            com.razorpay.Payment payment = client.payments.fetch(providerPaymentId);
            String status = payment.get("status");
            boolean captured = "captured".equalsIgnoreCase(status);
            return new ProviderPayment(
                    payment.get("id"),
                    payment.get("order_id"),
                    asLong(payment.get("amount")),
                    payment.get("currency"),
                    captured,
                    status,
                    payment.has("method") ? payment.get("method") : null,
                    errorDescription(payment),
                    payment.has("email") ? payment.get("email") : null,
                    payment.has("contact") ? payment.get("contact") : null);
        } catch (RazorpayException ex) {
            throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "The payment provider could not be reached: " + safeMessage(ex));
        }
    }

    @Override
    public boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId,
                                           String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", providerOrderId);
        attributes.put("razorpay_payment_id", providerPaymentId);
        attributes.put("razorpay_signature", signature);
        try {
            return Utils.verifyPaymentSignature(attributes, properties.getKeySecret());
        } catch (RazorpayException ex) {
            log.warn("Checkout signature verification failed: {}", safeMessage(ex));
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank() || rawBody == null) {
            return false;
        }
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            // Without a configured secret nothing can be verified, so nothing is trusted. Refusing is
            // the only safe answer: accepting unverified webhooks would let anyone confirm an order.
            log.warn("A webhook arrived but no webhook secret is configured; the event was refused");
            return false;
        }
        try {
            return Utils.verifyWebhookSignature(rawBody, signature, properties.getWebhookSecret());
        } catch (RazorpayException ex) {
            log.warn("Webhook signature verification failed: {}", safeMessage(ex));
            return false;
        }
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        JSONObject root = new JSONObject(rawBody);
        String eventType = root.optString("event", null);
        JSONObject payload = root.optJSONObject("payload");
        JSONObject payment = entity(payload, "payment");
        JSONObject refund = entity(payload, "refund");
        JSONObject dispute = entity(payload, "dispute");

        String providerPaymentId = null;
        String providerOrderId = null;
        Long amount = null;
        String currency = null;
        String method = null;
        String failureReason = null;
        String providerRefundId = null;
        String providerDisputeId = null;

        if (payment != null) {
            providerPaymentId = optString(payment, "id");
            providerOrderId = optString(payment, "order_id");
            amount = optLong(payment, "amount");
            currency = optString(payment, "currency");
            method = optString(payment, "method");
            failureReason = optString(payment, "error_description");
        }
        if (refund != null) {
            providerRefundId = optString(refund, "id");
            if (providerPaymentId == null) {
                providerPaymentId = optString(refund, "payment_id");
            }
            if (amount == null) {
                amount = optLong(refund, "amount");
            }
            if (currency == null) {
                currency = optString(refund, "currency");
            }
        }
        if (dispute != null) {
            providerDisputeId = optString(dispute, "id");
            if (providerPaymentId == null) {
                providerPaymentId = optString(dispute, "payment_id");
            }
            if (amount == null) {
                amount = optLong(dispute, "amount");
            }
            if (currency == null) {
                currency = optString(dispute, "currency");
            }
            failureReason = optString(dispute, "reason_code");
        }

        return new WebhookEvent(eventType, providerPaymentId, providerOrderId, amount, currency, method,
                failureReason, providerRefundId, providerDisputeId);
    }

    @Override
    public ProviderRefund refund(String providerPaymentId, Money amount, Map<String, String> notes) {
        JSONObject request = new JSONObject();
        request.put("amount", amount.minorUnits());
        if (notes != null && !notes.isEmpty()) {
            request.put("notes", new JSONObject(notes));
        }
        try {
            Refund refund = client.payments.refund(providerPaymentId, request);
            return new ProviderRefund(
                    refund.get("id"),
                    refund.get("payment_id"),
                    asLong(refund.get("amount")),
                    refund.get("currency"),
                    refund.get("status"));
        } catch (RazorpayException ex) {
            throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "The payment provider could not process the refund: " + safeMessage(ex));
        }
    }

    @Override
    public String publishableKey() {
        return properties.getKeyId();
    }

    // -------------------------------------------------------------------------------------------

    private static JSONObject entity(JSONObject payload, String name) {
        if (payload == null) {
            return null;
        }
        JSONObject wrapper = payload.optJSONObject(name);
        if (wrapper == null) {
            return null;
        }
        return wrapper.optJSONObject("entity") == null ? wrapper : wrapper.optJSONObject("entity");
    }

    private static String optString(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        return object.optString(key, null);
    }

    private static Long optLong(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        return object.optLong(key);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static String errorDescription(com.razorpay.Payment payment) {
        if (!payment.has("error_description")) {
            return null;
        }
        return payment.get("error_description");
    }

    /** A message safe to surface: the provider's code, never a body that might echo a credential. */
    private static String safeMessage(RazorpayException ex) {
        String code = ex.getCode();
        return code == null || code.isBlank() ? "provider error" : code;
    }
}
