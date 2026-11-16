package com.choco.identity.domain.port;

import com.choco.core.domain.EmailAddress;
import com.choco.identity.domain.admin.AdminAccount;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for administrator persistence.
 *
 * <p>Separate from {@link CustomerRepository} so an administrator can never be found or saved
 * through a customer-facing code path. There is deliberately no {@code existsByEmail} on the
 * customer side that a provisioning call could accidentally use.
 */
public interface AdminRepository {

    AdminAccount save(AdminAccount account);

    Optional<AdminAccount> findById(UUID id);

    Optional<AdminAccount> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    long count();
}
