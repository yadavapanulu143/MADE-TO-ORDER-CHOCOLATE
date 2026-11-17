package com.choco.order.application.dto;

import java.time.Instant;

public record OrderStatusChangeView(
        String fromStatus,
        String toStatus,
        String reason,
        Instant occurredAt) {
}
