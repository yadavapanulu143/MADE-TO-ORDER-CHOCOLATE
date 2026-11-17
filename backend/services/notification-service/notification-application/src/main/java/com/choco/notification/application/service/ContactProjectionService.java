package com.choco.notification.application.service;

import com.choco.core.port.Clock;
import com.choco.core.port.TransactionRunner;
import com.choco.notification.application.command.RecordContactCommand;
import com.choco.notification.application.port.ContactProjectionRepository;
import com.choco.notification.domain.contact.CustomerContact;
import com.choco.notification.domain.port.ContactDirectory;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains the local contact projection from the identity service's events.
 *
 * <p><b>Why a projection at all.</b> A notification needs a customer's email or mobile, but almost
 * every event that triggers one carries only a customer id — deliberately, so addresses are not
 * sprayed across every service's outbox and event stream. The alternative to this projection is a
 * synchronous call to the identity service on every send, which would put a network hop and a new
 * failure mode on the delivery path and make a message unsendable whenever identity is briefly down.
 * Keeping a local snapshot trades a little duplication for a delivery path with no synchronous
 * dependency.
 *
 * <p><b>A partial update is a merge, not an overwrite.</b> The identity service emits
 * {@code customer.registered} with both channels, but {@code customer.verified} with neither and a
 * contact-change event with only the changed channel. A null in the command therefore means "leave
 * what is known", not "clear it": overwriting a known mobile with null on a verification event would
 * erase the very number the service needs to text a delivery update.
 *
 * <p><b>The projection is derived, so it is written idempotently.</b> The same event may arrive twice;
 * the upsert simply writes the same values again.
 */
public class ContactProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ContactProjectionService.class);

    private final ContactProjectionRepository repository;
    private final ContactDirectory directory;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ContactProjectionService(ContactProjectionRepository repository, ContactDirectory directory,
                                    TransactionRunner transactions, Clock clock) {
        this.repository = repository;
        this.directory = directory;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Records or refreshes a customer's contact details.
     *
     * <p>Merges with what is already known: a field the event does not carry keeps its previous value.
     * The very first event for a customer creates the row; later events update it in place.
     */
    public void record(RecordContactCommand command) {
        transactions.execute(() -> {
            Optional<CustomerContact> current = directory.find(command.customerId());
            String email = firstNonBlank(command.email(), current.map(CustomerContact::email).orElse(null));
            String mobile = firstNonBlank(command.mobile(), current.map(CustomerContact::mobile).orElse(null));
            String fullName = firstNonBlank(command.fullName(),
                    current.map(CustomerContact::fullName).orElse(null));

            if (isBlank(email) && isBlank(mobile)) {
                // Nothing to address a message to. Do not create a row that could never be used; a
                // later event that does carry an address will create it.
                log.debug("Contact event for {} carried no reachable address; nothing to record",
                        command.customerId());
                return;
            }
            repository.upsert(new CustomerContact(command.customerId(), email, mobile, fullName,
                    command.occurredAt() == null ? clock.now() : command.occurredAt()));
        });
    }

    /** Removes a projection when a customer is deleted, so a stale address cannot be messaged. */
    public void remove(UUID customerId) {
        transactions.execute(() -> repository.delete(customerId));
    }

    private static String firstNonBlank(String candidate, String fallback) {
        return isBlank(candidate) ? fallback : candidate;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
