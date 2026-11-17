package com.choco.order.adapter.out.persistence;

import com.choco.order.domain.idempotency.IdempotencyRecord;
import com.choco.order.domain.port.IdempotencyRecordRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * JPA implementation of {@link IdempotencyRecordRepository}.
 *
 * <p>No optimistic locking: the record is written once and never updated. The guarantee that a key is
 * used once comes from the unique constraint on {@code (scope, key)} — a concurrent second insert
 * fails at the database rather than being caught by a version check.
 */
@Repository
class IdempotencyRecordRepositoryAdapter implements IdempotencyRecordRepository {

    private final SpringDataIdempotencyRepository delegate;

    IdempotencyRecordRepositoryAdapter(SpringDataIdempotencyRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key) {
        return delegate.findByScopeAndKey(scope, key).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        delegate.save(OrderPersistenceMapper.toEntity(record));
        return record;
    }
}
