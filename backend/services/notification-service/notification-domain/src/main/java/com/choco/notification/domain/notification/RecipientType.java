package com.choco.notification.domain.notification;

/**
 * Who a notification is for.
 *
 * <p>The distinction is not cosmetic: a customer notification is addressed to the account holder from
 * the event, while an administrative notification goes to the seller's configured address. Keeping it
 * explicit means a refund request or a dispute can never be routed to the customer by a template
 * mistake, and the seller can never be spammed with a customer's order confirmation.
 */
public enum RecipientType {
    CUSTOMER,
    ADMIN
}
