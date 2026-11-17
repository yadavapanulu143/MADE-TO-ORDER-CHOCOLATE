package com.choco.catalog.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when a product is withdrawn from sale, either archived or returned to draft. */
public record ProductUnpublished(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String status) implements DomainEvent {

    @Override
    public String eventType() {
        return "catalog.product-unpublished";
    }
}
