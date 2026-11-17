package com.choco.notification.application.port;

import com.choco.notification.domain.contact.CustomerContact;

/**
 * Write side of the customer-contact projection.
 *
 * <p>Only the identity-event consumer uses this. Separated from the read-side
 * {@code ContactDirectory} so a use case that sends a message cannot mutate the projection it reads.
 * An upsert, not a create: the same customer id arrives again on every registration, contact change
 * and verification, and the newest snapshot wins.
 */
public interface ContactProjectionRepository {

    void upsert(CustomerContact contact);

    void delete(java.util.UUID customerId);
}
