package com.choco.identity.application.fake;

import com.choco.identity.application.port.PasswordResetTokenRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final Map<UUID, StoredResetToken> byId = new LinkedHashMap<>();

    @Override
    public void store(UUID id, UUID customerId, String tokenHash, Instant expiresAt, Instant issuedAt) {
        byId.put(id, new StoredResetToken(id, customerId, tokenHash, expiresAt, issuedAt, null));
    }

    @Override
    public Optional<StoredResetToken> findByHash(String tokenHash) {
        return byId.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public void consume(UUID id, Instant consumedAt) {
        StoredResetToken existing = byId.get(id);
        if (existing != null) {
            byId.put(id, new StoredResetToken(existing.id(), existing.customerId(), existing.tokenHash(),
                    existing.expiresAt(), existing.issuedAt(), consumedAt));
        }
    }

    @Override
    public int consumeAllForCustomer(UUID customerId, Instant consumedAt) {
        List<UUID> open = byId.values().stream()
                .filter(t -> t.customerId().equals(customerId) && t.consumedAt() == null)
                .map(StoredResetToken::id)
                .toList();
        open.forEach(id -> consume(id, consumedAt));
        return open.size();
    }
}
