package com.choco.notification.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a notification could not be delivered and will not be retried.
 *
 * <p>Distinct from a failed attempt that will be retried: this is the terminal outcome after the retry
 * budget is spent. It exists so the seller has a signal that a customer never got a message — silence
 * would be indistinguishable from success.
 *
 * <p>The failure reason is carried but must be a provider-neutral summary, not a raw provider response:
 * a transport error body can contain the destination address, and this event is not the place for it.
 */
public record NotificationFailed(
        UUID eventId,
        Instant occurredAt,
        String notificationId,
        String type,
        String channel,
        String recipientType,
        String referenceId,
        String reason) implements DomainEvent {

    @Override
    public String eventType() {
        return "notification.failed";
    }

    @Override
    public String aggregateId() {
        return notificationId;
    }
}
