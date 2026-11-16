package com.choco.identity.application.fake;

import com.choco.identity.application.port.AdminRefreshTokenRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAdminRefreshTokenRepository implements AdminRefreshTokenRepository {

    private final Map<UUID, StoredAdminRefreshToken> byId = new LinkedHashMap<>();

    @Override
    public void store(UUID id, UUID adminId, String tokenHash, Instant expiresAt, Instant issuedAt) {
        byId.put(id, new StoredAdminRefreshToken(id, adminId, tokenHash, expiresAt, issuedAt, null));
    }

    @Override
    public Optional<StoredAdminRefreshToken> findByHash(String tokenHash) {
        return byId.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public void revoke(UUID id, Instant revokedAt) {
        StoredAdminRefreshToken existing = byId.get(id);
        if (existing != null) {
            byId.put(id, new StoredAdminRefreshToken(existing.id(), existing.adminId(), existing.tokenHash(),
                    existing.expiresAt(), existing.issuedAt(), revokedAt));
        }
    }

    @Override
    public int revokeAllForAdmin(UUID adminId, Instant revokedAt) {
        List<UUID> active = new ArrayList<>();
        byId.values().stream()
                .filter(t -> t.adminId().equals(adminId) && t.revokedAt() == null)
                .forEach(t -> active.add(t.id()));
        active.forEach(id -> revoke(id, revokedAt));
        return active.size();
    }
}
