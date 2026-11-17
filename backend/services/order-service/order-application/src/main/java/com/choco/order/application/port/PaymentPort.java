package com.choco.order.application.port;

import com.choco.core.domain.Money;
import java.util.UUID;

/**
 * Starts a payment for an order at the payment service.
 *
 * <p>The order service does not take money and does not decide that a payment succeeded; it asks the
 * payment service to create a provider order for an amount it already knows, and hands the browser what
 * it needs to open the provider's checkout. Everything about whether the money arrived comes back the
 * other way, as a verified event — never from this call's response.
 *
 * <p>The amount is passed in rather than derived here, because the order service is the authority on
 * what an order costs: the figure the customer agreed to at checkout is the figure the provider is
 * asked to collect. The port states that contract so an implementation cannot quietly ask for a
 * different amount.
 *
 * <p>Idempotency is a parameter, not an afterthought. The call is a {@code POST} that creates a
 * provider order, so a timeout followed by a retry must not create a second one; the caller supplies a
 * key and the payment service answers a replay with the first result.
 */
public interface PaymentPort {

    /**
     * The provider checkout parameters the browser needs, and nothing more.
     *
     * <p>It carries no field a browser could use to claim a payment succeeded — no "paid" flag, no
     * status the storefront could trust. Confirmation only ever arrives as a verified event.
     */
    record Initiation(
            String provider,
            String providerOrderId,
            long amountMinorUnits,
            String amount,
            String currency,
            String receipt,
            String publicKey) {
    }

    /**
     * Creates (or returns the existing) provider order for {@code orderId} and amount.
     *
     * @param orderId       the order the payment is for; also the idempotency scope at the provider
     * @param amount        the authoritative amount to collect, from the order's frozen total
     * @param receipt       the human-readable reference the provider shows, the order number
     * @param idempotencyKey a client-supplied key so a retried call returns the first provider order
     */
    Initiation initiate(UUID orderId, Money amount, String receipt, String idempotencyKey);
}
