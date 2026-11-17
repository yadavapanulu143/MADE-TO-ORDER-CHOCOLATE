package com.choco.notification.adapter.out.persistence;

import com.choco.notification.application.port.ContactProjectionRepository;
import com.choco.notification.domain.contact.CustomerContact;
import com.choco.notification.domain.port.ContactDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements both sides of the contact projection: the read port the delivery path uses and the write
 * port the identity-event consumer uses.
 *
 * <p>One adapter for both, because they are the same table and splitting them across two beans would
 * buy nothing but a second place to keep the mapping. The <em>interfaces</em> are still separate, which
 * is where the safety comes from: a use case that sends a message is handed only the read port and
 * therefore cannot write the projection even by mistake.
 *
 * <p>The upsert loads the row and writes the values it is given; the merge that decides which fields
 * to keep when an event carries only some of them happens in the application service, which can see
 * the previous row through the read port. Keeping the merge there means the rule "a null means leave
 * unchanged" is testable without a database.
 *
 * <p>The load-and-save runs inside the caller's transaction, so a concurrent update to the same
 * customer is serialised by the row lock rather than silently overwritten.
 */
@Repository
class ContactProjectionAdapter implements ContactProjectionRepository, ContactDirectory {

    private final SpringDataCustomerContactRepository delegate;

    ContactProjectionAdapter(SpringDataCustomerContactRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void upsert(CustomerContact contact) {
        CustomerContactJpaEntity entity = delegate.findById(contact.customerId())
                .orElseGet(CustomerContactJpaEntity::new);
        entity.setCustomerId(contact.customerId());
        // A null in the projection means the caller did not supply that channel; the merge already
        // resolved "leave unchanged" against the previous row, so writing the resolved value here is
        // correct even when it is null.
        entity.setEmail(contact.email());
        entity.setMobile(contact.mobile());
        entity.setFullName(contact.fullName());
        entity.setUpdatedAt(contact.updatedAt());
        delegate.save(entity);
    }

    @Override
    @Transactional
    public void delete(UUID customerId) {
        delegate.deleteById(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerContact> find(UUID customerId) {
        return delegate.findById(customerId).map(entity -> new CustomerContact(
                entity.getCustomerId(),
                entity.getEmail(),
                entity.getMobile(),
                entity.getFullName(),
                entity.getUpdatedAt()));
    }
}
