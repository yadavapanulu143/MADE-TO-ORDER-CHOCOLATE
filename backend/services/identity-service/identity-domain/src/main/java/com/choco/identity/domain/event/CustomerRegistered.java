package com.choco.identity.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record CustomerRegistered(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String email,
        String mobile,
        String fullName) implements DomainEvent {

    @Override
    public String eventType() {
        return "customer.registered";
    }
}
