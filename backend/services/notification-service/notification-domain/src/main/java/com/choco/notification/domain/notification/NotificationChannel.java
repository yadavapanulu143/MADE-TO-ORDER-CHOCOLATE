package com.choco.notification.domain.notification;

/**
 * How a notification reaches the recipient.
 *
 * <p>Only two channels exist because only two are required: email and SMS. Each notification is
 * delivered on exactly one channel, so a channel is part of a notification's identity rather than a
 * list on it. Sending the same message on both is two notifications, which is deliberate: a customer
 * who has given both an address and a number can be reached even if one is wrong.
 */
public enum NotificationChannel {
    EMAIL,
    SMS
}
