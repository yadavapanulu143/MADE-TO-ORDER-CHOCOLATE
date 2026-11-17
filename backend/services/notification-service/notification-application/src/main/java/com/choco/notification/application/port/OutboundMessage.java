package com.choco.notification.application.port;

import com.choco.notification.domain.notification.NotificationChannel;

/**
 * A fully rendered message, ready to hand to a provider.
 *
 * <p>Both a text and an HTML body travel together because an email client may render either: the text
 * part is what a plain client shows and what a screen reader reads, and the HTML part is the
 * formatted version. An SMS carries only {@code textBody}, and its {@code subject} and
 * {@code htmlBody} are null.
 *
 * <p>The recipient is the resolved destination — the customer's email or mobile, or the seller's —
 * and never a customer id. Resolution happens before this point so a transport adapter holds no
 * knowledge of how to look a customer up.
 */
public record OutboundMessage(
        NotificationChannel channel,
        String recipient,
        String subject,
        String textBody,
        String htmlBody) {

    public OutboundMessage {
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is required");
        }
        if (textBody == null || textBody.isBlank()) {
            throw new IllegalArgumentException("textBody is required");
        }
    }

    public static OutboundMessage sms(String mobile, String text) {
        return new OutboundMessage(NotificationChannel.SMS, mobile, null, text, null);
    }

    public static OutboundMessage email(String address, String subject, String text, String html) {
        return new OutboundMessage(NotificationChannel.EMAIL, address, subject, text, html);
    }
}
