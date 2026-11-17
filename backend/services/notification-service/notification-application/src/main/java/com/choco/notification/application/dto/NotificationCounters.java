package com.choco.notification.application.dto;

/**
 * The seller's at-a-glance notification health.
 *
 * <p>Pending is the number still waiting to go out, sent is the delivered total, and dead-letter is
 * the number that finally failed — the one that needs attention, because each represents a customer
 * who never got a message.
 */
public record NotificationCounters(long pending, long sent, long failed, long deadLetter, long suppressed) {
}
