package com.choco.identity.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Deliberately carries no password material, not even a hash. */
public record CustomerPasswordChanged(
        UUID eventId,
        Instant occurredAt,
        String aggregateId) implements DomainEvent {

    @Override
    public String eventType() {
        return "customer.password-changed";
    }
}
