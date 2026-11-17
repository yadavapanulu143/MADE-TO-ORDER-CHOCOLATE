package com.choco.notification.domain.notification;

import java.time.Instant;
import java.util.UUID;

/** Shared fixtures for the notification aggregate tests. */
final class NotificationTestSupport {

    static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");
    static final Instant LATER = T0.plusSeconds(600);
    static final UUID NOTIFICATION_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");

    private NotificationTestSupport() {
    }

    static Notification scheduled() {
        return Notification.schedule(NOTIFICATION_ID, NotificationType.ORDER_CONFIRMED,
                RecipientType.CUSTOMER, "asha@example.com", "Your order is confirmed",
                "Your order CHOCO-1 is confirmed.", "order.confirmed:order-1", "order-1", T0);
    }
}
