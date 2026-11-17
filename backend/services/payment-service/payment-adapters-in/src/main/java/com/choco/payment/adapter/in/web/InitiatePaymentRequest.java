package com.choco.payment.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A request from the order service to start a payment.
 *
 * <p>The amount is in paise and must be positive. The customer id is deliberately absent: it comes
 * from the caller's token, so it cannot be supplied by whoever is making the request.
 */
record InitiatePaymentRequest(
        @NotNull @Positive Long amountMinorUnits,
        @NotBlank String currency,
        String receipt) {
}
