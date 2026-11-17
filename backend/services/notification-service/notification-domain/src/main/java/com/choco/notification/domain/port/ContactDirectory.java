package com.choco.notification.domain.port;

import com.choco.notification.domain.contact.CustomerContact;
import java.util.Optional;
import java.util.UUID;

/**
 * The read side of the customer-contact projection.
 *
 * <p>Distinct from the write side ({@code CustomerContactRepository}) so the delivery path depends only
 * on being able to read a contact, while only the identity-event consumer needs to write one. A use
 * case that sends a message therefore cannot accidentally mutate the projection.
 */
public interface ContactDirectory {

    Optional<CustomerContact> find(UUID customerId);
}
