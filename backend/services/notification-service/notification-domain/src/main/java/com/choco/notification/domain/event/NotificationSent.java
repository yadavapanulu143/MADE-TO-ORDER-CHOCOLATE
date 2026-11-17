package com.choco.notification.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a notification has been handed to the provider and accepted.
 *
 * <p>Carries only identifiers and the channel, never the message body or the destination: this event
 * travels the bus and lands in other services' logs, and a message body can contain an order's
 * contents while a destination is a customer's email or mobile number. Neither belongs on the wire.
 *
 * <p>"Sent" means the provider accepted it, not that the customer read it. There is no delivery
 * receipt, so nothing here claims one.
 */
public record NotificationSent(
        UUID eventId,
        Instant occurredAt,
        String notificationId,
        String type,
        String channel,
        String recipientType,
        String referenceId) implements DomainEvent {

    @Override
    public String eventType() {
        return "notification.sent";
    }

    @Override
    public String aggregateId() {
        return notificationId;
    }
}
