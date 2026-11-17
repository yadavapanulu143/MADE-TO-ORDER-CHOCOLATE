package com.choco.identity.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a customer changes their email address or mobile number.
 *
 * <p>Carries the customer's <em>current</em> contact details, not only the changed field. The
 * notification service keeps a local projection of where to reach a customer, and a full snapshot
 * means that projection is a plain overwrite rather than a merge it has to reason about. The same
 * reasoning applies to {@code customer.registered}: the fact that a customer can be reached at these
 * details is the whole content of the event.
 *
 * <p>No password material is present, by construction: the event carries only what is needed to
 * address a message.
 */
public record CustomerContactChanged(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String email,
        String mobile,
        String fullName) implements DomainEvent {

    @Override
    public String eventType() {
        return "customer.contact-changed";
    }
}
