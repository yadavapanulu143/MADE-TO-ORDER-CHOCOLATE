package com.choco.fulfilment.application.dto;

/**
 * The answer the order service's checkout depends on: whether the seller delivers to a PIN code, and
 * at what charge.
 *
 * <p>A PIN code the seller does not cover is a definite "no" with a reason, not an error, because the
 * checkout turns it into a message for the customer. The charge is in paise, with the readable amount
 * alongside it.
 */
public record ServiceabilityQuoteView(
        boolean serviceable,
        String pincode,
        long deliveryChargeMinorUnits,
        String deliveryCharge,
        String currency,
        String reason) {
}
