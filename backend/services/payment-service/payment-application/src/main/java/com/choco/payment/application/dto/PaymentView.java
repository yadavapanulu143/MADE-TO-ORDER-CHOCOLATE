package com.choco.payment.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A payment as an administrator or the paying customer may see it. */
public record PaymentView(
        UUID id,
        UUID orderId,
        UUID customerId,
        long amountMinorUnits,
        String amount,
        String currency,
        String status,
        String provider,
        String providerOrderId,
        String providerPaymentId,
        String method,
        String failureReason,
        long refundedAmountMinorUnits,
        String refundedAmount,
        Instant createdAt,
        Instant updatedAt,
        List<RefundView> refunds,
        List<AttemptView> attempts) {
}
