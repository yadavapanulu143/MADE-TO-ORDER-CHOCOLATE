package com.choco.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

/** A row in a payment listing. */
public record PaymentSummaryView(
        UUID id,
        UUID orderId,
        long amountMinorUnits,
        String amount,
        String currency,
        String status,
        String method,
        Instant createdAt) {
}
