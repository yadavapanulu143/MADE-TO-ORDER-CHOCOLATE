package com.choco.identity.adapter.out.persistence;

import com.choco.core.domain.EmailAddress;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.identity.domain.admin.AdminAccount;
import com.choco.identity.domain.port.AdminRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * JPA implementation of {@link AdminRepository}.
 *
 * <p>As with the customer adapter, the unique index on the email is the real guarantee. The
 * pre-check exists so a normal duplicate gives a friendly error, and a concurrent insert that wins
 * the race is translated into the same {@link ConflictException}.
 */
@Repository
class AdminRepositoryAdapter implements AdminRepository {

    private final SpringDataAdminRepository delegate;

    AdminRepositoryAdapter(SpringDataAdminRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public AdminAccount save(AdminAccount account) {
        try {
            delegate.save(PersistenceMapper.toEntity(account));
        } catch (DataIntegrityViolationException e) {
            String detail = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
            if (detail != null && detail.toLowerCase(java.util.Locale.ROOT).contains("email")) {
                throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                        "An account already exists for this email address");
            }
            throw e;
        }
        return account;
    }

    @Override
    public Optional<AdminAccount> findById(UUID id) {
        return delegate.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<AdminAccount> findByEmail(EmailAddress email) {
        return delegate.findByEmail(email.value()).map(PersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return delegate.existsByEmail(email.value());
    }

    @Override
    public long count() {
        return delegate.count();
    }
}
