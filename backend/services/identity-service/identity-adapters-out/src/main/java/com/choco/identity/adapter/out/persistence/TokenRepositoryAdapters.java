package com.choco.identity.adapter.out.persistence;

import com.choco.identity.application.port.PasswordResetTokenRepository;
import com.choco.identity.application.port.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementations of the token repositories.
 *
 * <p>Bulk revocation is a single {@code UPDATE} rather than a read-modify-write loop. This matters
 * for correctness, not just speed: revoking sessions while another request is rotating a token
 * must not leave a valid token behind, and a set-based update cannot miss rows that a loop over a
 * stale snapshot would.
 */
final class TokenRepositoryAdapters {

    private TokenRepositoryAdapters() {
    }

    interface SpringDataRefreshTokens extends JpaRepository<RefreshTokenJpaEntity, UUID> {

        Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update RefreshTokenJpaEntity t set t.revokedAt = :revokedAt "
                + "where t.customerId = :customerId and t.revokedAt is null")
        int revokeAll(@Param("customerId") UUID customerId, @Param("revokedAt") Instant revokedAt);
    }

    interface SpringDataResetTokens extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

        Optional<PasswordResetTokenJpaEntity> findByTokenHash(String tokenHash);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update PasswordResetTokenJpaEntity t set t.consumedAt = :consumedAt "
                + "where t.customerId = :customerId and t.consumedAt is null")
        int consumeAll(@Param("customerId") UUID customerId, @Param("consumedAt") Instant consumedAt);
    }

    @Repository
    static class RefreshTokenAdapter implements RefreshTokenRepository {

        private final SpringDataRefreshTokens delegate;

        RefreshTokenAdapter(SpringDataRefreshTokens delegate) {
            this.delegate = delegate;
        }

        @Override
        public void store(UUID id, UUID customerId, String tokenHash, Instant expiresAt, Instant issuedAt) {
            RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
            entity.setId(id);
            entity.setCustomerId(customerId);
            entity.setTokenHash(tokenHash);
            entity.setExpiresAt(expiresAt);
            entity.setIssuedAt(issuedAt);
            delegate.save(entity);
        }

        @Override
        public Optional<StoredRefreshToken> findByHash(String tokenHash) {
            return delegate.findByTokenHash(tokenHash).map(e -> new StoredRefreshToken(e.getId(),
                    e.getCustomerId(), e.getTokenHash(), e.getExpiresAt(), e.getIssuedAt(), e.getRevokedAt()));
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
        public int revokeAllForCustomer(UUID customerId, Instant revokedAt) {
            return delegate.revokeAll(customerId, revokedAt);
        }
    }

    @Repository
    static class PasswordResetTokenAdapter implements PasswordResetTokenRepository {

        private final SpringDataResetTokens delegate;

        PasswordResetTokenAdapter(SpringDataResetTokens delegate) {
            this.delegate = delegate;
        }

        @Override
        public void store(UUID id, UUID customerId, String tokenHash, Instant expiresAt, Instant issuedAt) {
            PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
            entity.setId(id);
            entity.setCustomerId(customerId);
            entity.setTokenHash(tokenHash);
            entity.setExpiresAt(expiresAt);
            entity.setIssuedAt(issuedAt);
            delegate.save(entity);
        }

        @Override
        public Optional<StoredResetToken> findByHash(String tokenHash) {
            return delegate.findByTokenHash(tokenHash).map(e -> new StoredResetToken(e.getId(),
                    e.getCustomerId(), e.getTokenHash(), e.getExpiresAt(), e.getIssuedAt(), e.getConsumedAt()));
        }

        @Override
        @Transactional
        public void consume(UUID id, Instant consumedAt) {
            delegate.findById(id).ifPresent(entity -> {
                entity.setConsumedAt(consumedAt);
                delegate.save(entity);
            });
        }

        @Override
        @Transactional
        public int consumeAllForCustomer(UUID customerId, Instant consumedAt) {
            return delegate.consumeAll(customerId, consumedAt);
        }
    }
}
