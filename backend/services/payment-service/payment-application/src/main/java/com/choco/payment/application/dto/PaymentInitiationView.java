package com.choco.payment.application.dto;

/**
 * What the storefront needs to open the provider's checkout.
 *
 * <p>Carries the provider's order id, the amount and the publishable key the browser needs. It
 * carries nothing that would let a browser decide the payment succeeded — that only ever comes from a
 * verified webhook or a server-side verification.
 */
public record PaymentInitiationView(
        String provider,
        String providerOrderId,
        long amountMinorUnits,
        String amount,
        String currency,
        String receipt,
        String publicKey) {
}
