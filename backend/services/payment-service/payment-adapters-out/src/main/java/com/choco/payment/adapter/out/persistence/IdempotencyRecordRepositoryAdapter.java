package com.choco.payment.adapter.out.persistence;

import com.choco.payment.domain.idempotency.IdempotencyRecord;
import com.choco.payment.domain.port.IdempotencyRecordRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of {@link IdempotencyRecordRepository}. */
@Repository
class IdempotencyRecordRepositoryAdapter implements IdempotencyRecordRepository {

    private final SpringDataIdempotencyRepository delegate;

    IdempotencyRecordRepositoryAdapter(SpringDataIdempotencyRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key) {
        return delegate.findByScopeAndKey(scope, key).map(IdempotencyRecordRepositoryAdapter::toDomain);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        delegate.save(toEntity(record));
        return record;
    }

    private static IdempotencyRecord toDomain(IdempotencyRecordJpaEntity entity) {
        return IdempotencyRecord.of(entity.getId(), entity.getScope(), entity.getKey(),
                entity.getRequestFingerprint(), entity.getResourceId(), entity.getResponseBody(),
                entity.getResponseStatus(), entity.getCreatedAt(), entity.getExpiresAt());
    }

    private static IdempotencyRecordJpaEntity toEntity(IdempotencyRecord record) {
        IdempotencyRecordJpaEntity entity = new IdempotencyRecordJpaEntity();
        entity.setId(record.id());
        entity.setScope(record.scope());
        entity.setKey(record.key());
        entity.setRequestFingerprint(record.requestFingerprint());
        entity.setResourceId(record.resourceId());
        entity.setResponseBody(record.responseBody());
        entity.setResponseStatus(record.responseStatus());
        entity.setCreatedAt(record.createdAt());
        entity.setExpiresAt(record.expiresAt());
        return entity;
    }
}
