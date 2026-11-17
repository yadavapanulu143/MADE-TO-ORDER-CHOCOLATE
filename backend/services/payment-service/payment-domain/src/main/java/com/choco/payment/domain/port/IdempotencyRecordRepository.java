package com.choco.payment.domain.port;

import com.choco.payment.domain.idempotency.IdempotencyRecord;
import java.util.Optional;

/** Persistence port for idempotency records. */
public interface IdempotencyRecordRepository {

    Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key);

    IdempotencyRecord save(IdempotencyRecord record);
}
