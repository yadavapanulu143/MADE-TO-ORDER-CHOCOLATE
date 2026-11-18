package com.choco.notification.domain.notification;

/**
 * The catalogue of messages this service can send.
 *
 * <p>Each constant names a business message and states, as business policy, the channel it travels on
 * and who it is for. Keeping that decision here — rather than in a template file or a configuration
 * map — means a new notification cannot be added without someone consciously choosing its audience:
 * a refund decision must reach the customer, a low-stock warning must reach the seller, and neither
 * can be swapped by a typo in a properties file.
 *
 * <p>The channel is a deliberate choice per message, not a global default. Time-critical delivery
 * steps go by SMS because a customer is more likely to see them promptly; everything else goes by
 * email, where the full order detail can be included.
 */
public enum NotificationType {

    ORDER_PLACED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    ORDER_CONFIRMED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    ORDER_CANCELLED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    INVOICE_ISSUED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    PAYMENT_SUCCESS(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    PAYMENT_FAILED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    PAYMENT_PENDING(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    REFUND_REQUESTED(NotificationChannel.EMAIL, RecipientType.ADMIN),
    REFUND_PROCESSED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    DISPUTE_RAISED(NotificationChannel.EMAIL, RecipientType.ADMIN),
    PRODUCTION_STARTED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    PRODUCTION_COMPLETED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    READY_FOR_DISPATCH(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    DELIVERY_ASSIGNED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    OUT_FOR_DELIVERY(NotificationChannel.SMS, RecipientType.CUSTOMER),
    DELIVERY_DELIVERED(NotificationChannel.SMS, RecipientType.CUSTOMER),
    DELIVERY_FAILED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    DELIVERY_RESCHEDULED(NotificationChannel.EMAIL, RecipientType.CUSTOMER),
    LOW_STOCK_ALERT(NotificationChannel.EMAIL, RecipientType.ADMIN);

    private final NotificationChannel channel;
    private final RecipientType recipientType;

    NotificationType(NotificationChannel channel, RecipientType recipientType) {
        this.channel = channel;
        this.recipientType = recipientType;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public RecipientType recipientType() {
        return recipientType;
    }
}
