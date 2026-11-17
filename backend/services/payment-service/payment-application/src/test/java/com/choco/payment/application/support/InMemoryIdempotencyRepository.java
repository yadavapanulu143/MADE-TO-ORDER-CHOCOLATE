package com.choco.payment.application.support;

import com.choco.payment.domain.idempotency.IdempotencyRecord;
import com.choco.payment.domain.port.IdempotencyRecordRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link IdempotencyRecordRepository} for use-case tests. */
public final class InMemoryIdempotencyRepository implements IdempotencyRecordRepository {

    private final Map<String, IdempotencyRecord> byScopeAndKey = new LinkedHashMap<>();

    @Override
    public Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key) {
        return Optional.ofNullable(byScopeAndKey.get(scope + '\u0000' + key));
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        byScopeAndKey.put(record.scope() + '\u0000' + record.key(), record);
        return record;
    }
}
