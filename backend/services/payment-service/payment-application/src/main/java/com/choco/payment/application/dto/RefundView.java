package com.choco.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

/** A refund as it appears in a payment record. */
public record RefundView(
        UUID id,
        long amountMinorUnits,
        String amount,
        String status,
        String reason,
        String providerRefundId,
        String failureReason,
        Instant requestedAt,
        Instant processedAt) {
}
