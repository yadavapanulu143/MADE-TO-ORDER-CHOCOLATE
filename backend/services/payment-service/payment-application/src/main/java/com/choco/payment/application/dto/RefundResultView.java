package com.choco.payment.application.dto;

import java.util.UUID;

/** The outcome of a seller-initiated refund. */
public record RefundResultView(
        UUID refundId,
        UUID paymentId,
        long amountMinorUnits,
        String amount,
        String status,
        String providerRefundId) {
}
