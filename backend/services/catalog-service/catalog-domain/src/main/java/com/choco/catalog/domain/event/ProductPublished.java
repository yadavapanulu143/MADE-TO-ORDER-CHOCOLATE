package com.choco.catalog.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when a product becomes visible and orderable in the customer catalogue. */
public record ProductPublished(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String slug) implements DomainEvent {

    @Override
    public String eventType() {
        return "catalog.product-published";
    }
}
