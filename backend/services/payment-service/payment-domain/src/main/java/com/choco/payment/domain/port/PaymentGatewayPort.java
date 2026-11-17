package com.choco.payment.domain.port;

import com.choco.core.domain.Money;
import java.util.Map;

/**
 * Outbound port for the payment provider.
 *
 * <p>The domain and the use cases speak only in terms of this interface, so the provider SDK never
 * reaches them. That keeps the business rules testable with a fake gateway — which is how the
 * signature-verification and webhook flows are tested without a network — and leaves the provider
 * replaceable without touching a rule.
 *
 * <p>No method here returns or accepts anything sensitive. A card number, a CVV or a UPI PIN never
 * crosses this boundary, because the provider's own checkout collects them.
 */
public interface PaymentGatewayPort {

    /**
     * Creates a provider-side order for the amount, returning its identifier.
     *
     * <p>Carries a {@code receipt} derived from the order, so a payment can be traced back from the
     * provider's dashboard to the order without a join.
     */
    ProviderOrder createOrder(Money amount, String receipt, Map<String, String> notes);

    /**
     * Fetches a payment's authoritative state from the provider.
     *
     * <p>This is the server-side verification: the storefront calls back with a payment id, and the
     * service asks the provider directly whether that payment is captured, rather than believing the
     * browser. The result is what the domain uses to decide, not the redirect parameters.
     */
    ProviderPayment fetchPayment(String providerPaymentId);

    /**
     * Verifies a checkout signature.
     *
     * <p>The checkout flow returns an order id, a payment id and a signature computed by the provider
     * over the first two with the account secret. Verifying it proves the response came from the
     * provider and not from a forged request; it does not on its own prove the money moved, so the
     * verified payment is still fetched and checked.
     */
    boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId, String signature);

    /**
     * Verifies a webhook signature over the raw request body.
     *
     * <p>Computed with a separate webhook secret. The raw body must be passed unchanged: re-serialising
     * the parsed JSON would change whitespace and key order and the signature would never match.
     */
    boolean verifyWebhookSignature(String rawBody, String signature);

    /**
     * Parses a webhook body into the fields this service acts on.
     *
     * <p>Provider-shaped parsing is an infrastructure concern, so it lives behind this port and the
     * use cases only ever see {@link WebhookEvent}. Parsing is not verification: the signature is
     * checked separately, and a parsed event is acted on only once that check has passed.
     */
    WebhookEvent parseWebhook(String rawBody);

    /** Issues a refund against a captured payment, returning the provider's refund identifier. */
    ProviderRefund refund(String providerPaymentId, Money amount, Map<String, String> notes);

    /** The publishable key the browser needs to open the checkout. Never a secret. */
    String publishableKey();

    /** The provider-side order, reduced to what the service stores. */
    record ProviderOrder(String id, long amountMinorUnits, String currency, String status) {
    }

    /**
     * A payment as the provider reports it.
     *
     * @param captured         whether the provider reports the payment as captured (money taken)
     * @param amountMinorUnits the captured amount, checked against what was charged
     */
    record ProviderPayment(String id, String orderId, long amountMinorUnits, String currency,
                           boolean captured, String status, String method, String failureReason,
                           String email, String contact) {
    }

    /** A refund as the provider reports it. */
    record ProviderRefund(String id, String paymentId, long amountMinorUnits, String currency,
                          String status) {
    }

    /**
     * A webhook reduced to the fields the service acts on.
     *
     * @param eventType         the provider's event name, e.g. {@code payment.captured}
     * @param providerPaymentId the payment the event concerns, when present
     * @param providerOrderId   the provider order the event concerns, when present
     * @param amountMinorUnits  the amount the event reports, when present
     * @param currency          the currency the event reports, when present
     * @param method            the method, when the provider reports one
     * @param failureReason     the failure description, for a failure event
     * @param providerRefundId  the refund id, for a refund event
     * @param providerDisputeId the dispute id, for a dispute event
     */
    record WebhookEvent(String eventType, String providerPaymentId, String providerOrderId,
                        Long amountMinorUnits, String currency, String method, String failureReason,
                        String providerRefundId, String providerDisputeId) {
    }
}
