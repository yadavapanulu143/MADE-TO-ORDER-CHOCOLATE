package com.choco.order.adapter.out.client;

import com.choco.core.domain.Money;
import com.choco.order.application.port.PaymentPort;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Starts a payment by calling the payment service over HTTP.
 *
 * <p>The customer's own token is forwarded, so the payment service derives the customer from the token
 * and applies its own ownership rule. This client therefore cannot start a payment for someone else
 * even if the order service were wrong about who was asking.
 *
 * <p>The amount is sent exactly as the order states it. The payment service verifies a capture against
 * the amount it was asked to collect, so the order's frozen total is the figure that guards both ends:
 * a request here for a smaller amount would still be refused on the way back.
 *
 * <p>The response is the provider's order id and the publishable key, and nothing that proves payment.
 * This client returns that shape untouched — it does not, and must not, infer any payment outcome from
 * it.
 */
@Component
class PaymentClient implements PaymentPort {

    private final RestClient client;

    PaymentClient(DownstreamProperties properties, DownstreamTokenProvider tokens) {
        this.client = DownstreamClients.build(properties.getPayment(), properties.getConnectTimeout(),
                properties.getReadTimeout(), tokens);
    }

    @Override
    public Initiation initiate(UUID orderId, Money amount, String receipt, String idempotencyKey) {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                amount.minorUnits(), amount.currency().getCurrencyCode(), receipt);

        InitiationResponse body = DownstreamClients.call("payment", () -> client.post()
                .uri("/api/v1/payments/orders/{orderId}/initiate", orderId)
                .headers(headers -> {
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey.trim());
                    }
                })
                .body(request)
                .retrieve()
                .body(InitiationResponse.class));

        if (body == null || body.providerOrderId() == null) {
            // A 200 with no provider order id is not a usable answer. Treated as a dependency failure
            // rather than returned as a half-built initiation the storefront could not act on.
            throw DownstreamClients.unwrap(new IllegalStateException("empty payment initiation"),
                    "payment");
        }
        return new Initiation(
                body.provider(),
                body.providerOrderId(),
                body.amountMinorUnits(),
                body.amount(),
                body.currency(),
                body.receipt(),
                body.publicKey());
    }

    /**
     * The request body the payment service expects.
     *
     * <p>The customer id is deliberately absent: the payment service takes it from the forwarded token,
     * so it cannot be supplied here. Only the amount, its currency and the receipt are sent.
     */
    record InitiatePaymentRequest(long amountMinorUnits, String currency, String receipt) {
    }

    /**
     * The wire shape of a payment initiation.
     *
     * <p>A local record, not the payment service's class: the order service depends on the published
     * JSON contract, not on another service's Java types. Unknown fields are ignored, so the payment
     * service can add one without breaking this client.
     */
    record InitiationResponse(
            String provider,
            String providerOrderId,
            long amountMinorUnits,
            String amount,
            String currency,
            String receipt,
            String publicKey) {
    }
}
