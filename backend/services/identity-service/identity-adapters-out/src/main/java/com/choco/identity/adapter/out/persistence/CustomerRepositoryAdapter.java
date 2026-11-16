package com.choco.identity.adapter.out.persistence;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.port.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * JPA implementation of {@link CustomerRepository}.
 *
 * <p>The unique constraints on email and mobile are the real guarantee of uniqueness. The
 * application layer checks first so that a normal duplicate produces a friendly error, but two
 * simultaneous registrations of the same address can both pass that check. When the database
 * rejects the loser, this adapter translates the constraint violation into the same
 * {@link ConflictException} the pre-check would have thrown, so the caller sees one consistent
 * answer regardless of timing.
 */
@Repository
class CustomerRepositoryAdapter implements CustomerRepository {

    private final SpringDataCustomerRepository delegate;

    CustomerRepositoryAdapter(SpringDataCustomerRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Customer save(Customer customer) {
        try {
            delegate.save(PersistenceMapper.toEntity(customer));
        } catch (DataIntegrityViolationException e) {
            throw translate(customer, e);
        }
        return customer;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return delegate.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(EmailAddress email) {
        return delegate.findByEmail(email.value()).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<Customer> findByMobile(PhoneNumber mobile) {
        return delegate.findByMobile(mobile.value()).map(PersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return delegate.existsByEmail(email.value());
    }

    @Override
    public boolean existsByMobile(PhoneNumber mobile) {
        return delegate.existsByMobile(mobile.value());
    }

    private ConflictException translate(Customer customer, DataIntegrityViolationException e) {
        String detail = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
        if (detail == null) {
            detail = "";
        }
        String lower = detail.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("mobile")) {
            return new ConflictException(ErrorCode.MOBILE_ALREADY_REGISTERED,
                    "An account already exists for this mobile number");
        }
        if (lower.contains("email")) {
            return new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account already exists for this email address");
        }
        // Unknown constraint: surface it rather than mislabelling the conflict.
        throw e;
    }
}
