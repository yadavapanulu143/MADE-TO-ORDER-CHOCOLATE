package com.choco.catalog.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when a product is first recorded. It is a draft at this point, not yet on sale. */
public record ProductCreated(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String name,
        String slug) implements DomainEvent {

    @Override
    public String eventType() {
        return "catalog.product-created";
    }
}
