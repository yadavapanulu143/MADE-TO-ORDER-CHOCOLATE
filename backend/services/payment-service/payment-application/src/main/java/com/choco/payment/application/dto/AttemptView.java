package com.choco.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

/** One recorded attempt against a payment, newest last. */
public record AttemptView(
        UUID id,
        String providerPaymentId,
        String method,
        String outcome,
        String failureReason,
        Instant attemptedAt) {
}
