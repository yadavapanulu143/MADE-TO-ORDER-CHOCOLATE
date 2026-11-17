package com.choco.order.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The compact shape used in order lists.
 *
 * <p>Deliberately omits the address and lines: a history page shows many orders and none of them needs
 * the full detail, so returning it would be wasteful and would spread delivery addresses further than
 * necessary.
 */
public record OrderSummaryView(
        UUID id,
        String orderNumber,
        int totalItems,
        long totalMinorUnits,
        String total,
        String currency,
        String status,
        String paymentStatus,
        Instant createdAt) {
}
