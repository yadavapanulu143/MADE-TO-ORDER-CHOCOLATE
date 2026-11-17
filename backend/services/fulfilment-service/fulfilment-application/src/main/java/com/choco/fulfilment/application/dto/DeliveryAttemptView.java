package com.choco.fulfilment.application.dto;

import java.time.Instant;
import java.util.UUID;

/** One recorded delivery attempt. */
public record DeliveryAttemptView(
        UUID id,
        int attemptNumber,
        String outcome,
        String failureReason,
        String notes,
        Instant attemptedAt) {
}
