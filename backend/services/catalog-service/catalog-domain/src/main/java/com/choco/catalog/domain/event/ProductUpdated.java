package com.choco.catalog.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when a product's descriptive content changes. Carries no pricing, which has its own event. */
public record ProductUpdated(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String name) implements DomainEvent {

    @Override
    public String eventType() {
        return "catalog.product-updated";
    }
}
