package com.choco.notification.application.dto;

import com.choco.notification.domain.notification.Notification;
import java.time.Instant;
import java.util.UUID;

/**
 * Administrative view of a notification.
 *
 * <p>The body is deliberately excluded. The notification log is for the seller to see <em>whether</em>
 * a message was sent and how many attempts it took — not to re-read the contents of every message a
 * customer received. The recipient is masked, so a log page cannot be scraped for a customer list.
 */
public record NotificationView(
        UUID id,
        String type,
        String channel,
        String recipientType,
        String recipientMasked,
        String subject,
        String status,
        int attempts,
        String lastError,
        String referenceId,
        Instant createdAt,
        Instant sentAt) {

    public static NotificationView from(Notification notification) {
        return new NotificationView(
                notification.id(),
                notification.type().name(),
                notification.channel().name(),
                notification.recipientType().name(),
                mask(notification.recipient(), notification.channel().name()),
                notification.subject(),
                notification.status().name(),
                notification.attempts(),
                notification.lastError(),
                notification.referenceId(),
                notification.createdAt(),
                notification.sentAt());
    }

    /** Masks an address or number so the log never shows a full destination. */
    static String mask(String recipient, String channel) {
        if (recipient == null) {
            return "";
        }
        if ("SMS".equals(channel)) {
            return recipient.length() <= 4 ? "****" : "******" + recipient.substring(recipient.length() - 4);
        }
        int at = recipient.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return recipient.charAt(0) + "***" + recipient.charAt(at - 1) + recipient.substring(at);
    }
}
