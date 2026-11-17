package com.choco.order.application.support;

import com.choco.order.domain.idempotency.IdempotencyRecord;
import com.choco.order.domain.port.IdempotencyRecordRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryIdempotencyRepository implements IdempotencyRecordRepository {

    private final Map<String, IdempotencyRecord> byKey = new HashMap<>();

    @Override
    public Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key) {
        return Optional.ofNullable(byKey.get(scope + "::" + key));
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        byKey.put(record.scope() + "::" + record.key(), record);
        return record;
    }
}
