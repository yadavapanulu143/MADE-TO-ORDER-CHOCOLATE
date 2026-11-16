package com.choco.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for a business fact that has already happened. Published after the transaction commits,
 * so a consumer never sees an event for a change that was rolled back.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    /** Stable name used as the routing key, e.g. {@code order.placed}. */
    String eventType();

    /** Identifier of the aggregate that produced the event; keeps per-aggregate ordering. */
    String aggregateId();

    /** Version of this event's schema, bumped when the payload changes incompatibly. */
    default int schemaVersion() {
        return 1;
    }
}
