package com.choco.identity.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a single channel is verified. {@code fullyVerified} tells a consumer whether this was
 * the last outstanding step, so a welcome message is sent once and not twice.
 */
public record CustomerVerified(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String channel,
        boolean fullyVerified) implements DomainEvent {

    @Override
    public String eventType() {
        return "customer.verified";
    }
}
