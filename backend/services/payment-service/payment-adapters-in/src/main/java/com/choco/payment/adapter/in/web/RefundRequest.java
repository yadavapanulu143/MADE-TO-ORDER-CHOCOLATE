package com.choco.payment.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A seller-initiated refund.
 *
 * <p>The amount is in paise, matching every other monetary field on the platform, and must be
 * positive. The upper bound — what is still held — is enforced by the payment aggregate, not here, so
 * it cannot be bypassed by a caller that skips this validation.
 */
record RefundRequest(
        @NotNull @Positive Long amountMinorUnits,
        String reason) {
}
