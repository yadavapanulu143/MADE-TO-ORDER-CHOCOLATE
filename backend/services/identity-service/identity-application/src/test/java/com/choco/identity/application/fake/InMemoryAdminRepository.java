package com.choco.identity.application.fake;

import com.choco.core.domain.EmailAddress;
import com.choco.identity.domain.admin.AdminAccount;
import com.choco.identity.domain.port.AdminRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAdminRepository implements AdminRepository {

    private final Map<UUID, AdminAccount> byId = new LinkedHashMap<>();

    @Override
    public AdminAccount save(AdminAccount account) {
        byId.put(account.id(), account);
        return account;
    }

    @Override
    public Optional<AdminAccount> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<AdminAccount> findByEmail(EmailAddress email) {
        return byId.values().stream()
                .filter(a -> a.email().equals(email))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public long count() {
        return byId.size();
    }
}
