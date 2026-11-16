package com.choco.events.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches the oldest unpublished events.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} is what makes the relay safe to run on more than one replica:
     * two relays never publish the same row, and a relay never blocks on a row another relay holds.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findUnpublishedForUpdate(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    long countByPublishedAtIsNullAndAttemptsGreaterThan(int attempts);
}
