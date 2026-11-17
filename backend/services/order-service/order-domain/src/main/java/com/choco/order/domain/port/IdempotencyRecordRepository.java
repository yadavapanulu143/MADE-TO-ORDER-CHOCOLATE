package com.choco.order.domain.port;

import com.choco.order.domain.idempotency.IdempotencyRecord;
import java.util.Optional;

/**
 * Persistence port for idempotency records.
 *
 * <p>Placing an order and initiating a payment are the two operations a client may retry after a
 * timeout, and both must be safe to repeat. The record stores the first response so a replay returns
 * that response instead of creating a second order or a second charge.
 */
public interface IdempotencyRecordRepository {

    Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key);

    IdempotencyRecord save(IdempotencyRecord record);
}
