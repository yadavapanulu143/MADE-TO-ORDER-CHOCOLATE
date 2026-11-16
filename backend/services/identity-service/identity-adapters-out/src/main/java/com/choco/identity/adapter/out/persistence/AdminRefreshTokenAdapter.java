package com.choco.identity.adapter.out.persistence;

import com.choco.identity.application.port.AdminRefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link AdminRefreshTokenRepository}.
 *
 * <p>Bulk revocation is a single {@code UPDATE}, as with the customer tokens: a set-based update
 * cannot miss a row that a loop over a stale snapshot would.
 */
@Repository
class AdminRefreshTokenAdapter implements AdminRefreshTokenRepository {

    private final SpringDataAdminRefreshTokenRepository delegate;

    AdminRefreshTokenAdapter(SpringDataAdminRefreshTokenRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void store(UUID id, UUID adminId, String tokenHash, Instant expiresAt, Instant issuedAt) {
        AdminRefreshTokenJpaEntity entity = new AdminRefreshTokenJpaEntity();
        entity.setId(id);
        entity.setAdminId(adminId);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(expiresAt);
        entity.setIssuedAt(issuedAt);
        delegate.save(entity);
    }

    @Override
    public Optional<StoredAdminRefreshToken> findByHash(String tokenHash) {
        return delegate.findByTokenHash(tokenHash).map(e -> new StoredAdminRefreshToken(e.getId(),
                e.getAdminId(), e.getTokenHash(), e.getExpiresAt(), e.getIssuedAt(), e.getRevokedAt()));
    }

    @Override
    @Transactional
    public void revoke(UUID id, Instant revokedAt) {
        delegate.findById(id).ifPresent(entity -> {
            entity.setRevokedAt(revokedAt);
            delegate.save(entity);
        });
    }

    @Override
    @Transactional
    public int revokeAllForAdmin(UUID adminId, Instant revokedAt) {
        return delegate.revokeAll(adminId, revokedAt);
    }
}
