package com.choco.payment.application.command;

import java.util.UUID;

/**
 * The result of the provider's checkout, as reported by the browser.
 *
 * <p>Nothing here is trusted. The signature is checked against the account secret, and the payment is
 * then fetched from the provider and inspected; only a captured payment moves an order to paid. The
 * parameters exist so the service can find the payment and prove the response was not forged.
 */
public record VerifyPaymentCommand(
        UUID orderId,
        String providerOrderId,
        String providerPaymentId,
        String signature) {
}
