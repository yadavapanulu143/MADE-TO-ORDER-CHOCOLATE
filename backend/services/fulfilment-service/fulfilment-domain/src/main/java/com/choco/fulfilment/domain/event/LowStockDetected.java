package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an ingredient's level falls to or below its configured minimum.
 *
 * <p>Carries the current level and the minimum in thousandths of the ingredient's unit, so the
 * notification service can say "you have 250 g of cocoa left, below your 500 g threshold" without
 * reading this service's tables.
 */
public record LowStockDetected(
        UUID eventId,
        Instant occurredAt,
        String ingredientId,
        String ingredientName,
        String unit,
        long onHandMilliUnits,
        long minimumMilliUnits) implements DomainEvent {

    @Override
    public String eventType() {
        return "fulfilment.low-stock";
    }

    @Override
    public String aggregateId() {
        return ingredientId;
    }
}
