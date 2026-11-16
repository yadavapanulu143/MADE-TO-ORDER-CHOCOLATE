package com.choco.identity.application.fake;

import com.choco.identity.application.port.RefreshTokenRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<UUID, StoredRefreshToken> byId = new LinkedHashMap<>();

    @Override
    public void store(UUID id, UUID customerId, String tokenHash, Instant expiresAt, Instant issuedAt) {
        byId.put(id, new StoredRefreshToken(id, customerId, tokenHash, expiresAt, issuedAt, null));
    }

    @Override
    public Optional<StoredRefreshToken> findByHash(String tokenHash) {
        return byId.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public void revoke(UUID id, Instant revokedAt) {
        StoredRefreshToken existing = byId.get(id);
        if (existing != null) {
            byId.put(id, new StoredRefreshToken(existing.id(), existing.customerId(), existing.tokenHash(),
                    existing.expiresAt(), existing.issuedAt(), revokedAt));
        }
    }

    @Override
    public int revokeAllForCustomer(UUID customerId, Instant revokedAt) {
        List<UUID> active = new ArrayList<>();
        byId.values().stream()
                .filter(t -> t.customerId().equals(customerId) && t.revokedAt() == null)
                .forEach(t -> active.add(t.id()));
        active.forEach(id -> revoke(id, revokedAt));
        return active.size();
    }
}
