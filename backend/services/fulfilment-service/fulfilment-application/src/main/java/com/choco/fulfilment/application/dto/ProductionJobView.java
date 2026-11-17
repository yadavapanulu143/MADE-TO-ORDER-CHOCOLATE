package com.choco.fulfilment.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A production job as presented to the seller and, in a trimmed form, to the customer.
 *
 * <p>Carries the variant label and quantity so the kitchen sheet is self-contained, and the notes so
 * the seller sees the context without a second call. No price is carried: production is about making
 * chocolate, and the money lives on the order.
 */
public record ProductionJobView(
        UUID id,
        UUID orderId,
        String orderNumber,
        UUID customerId,
        UUID productId,
        UUID variantId,
        String variantLabel,
        int quantity,
        String status,
        String holdReason,
        boolean ingredientsConsumed,
        Instant receivedAt,
        Instant updatedAt,
        List<ProductionNoteView> notes) {
}
