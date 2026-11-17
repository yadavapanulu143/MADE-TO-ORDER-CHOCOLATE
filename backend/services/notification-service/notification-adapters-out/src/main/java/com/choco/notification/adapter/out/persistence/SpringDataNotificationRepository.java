package com.choco.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    Optional<NotificationJpaEntity> findByDedupeKey(String dedupeKey);

    /**
     * Reads only the optimistic-lock version of a row.
     *
     * <p>The save path rebuilds the entity from the aggregate, so it has to carry the current version
     * across for Hibernate to compare against the row as it was loaded.
     */
    @Query("select n.version from NotificationJpaEntity n where n.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    /**
     * The recovery query: pending or failed notifications whose last update is old enough to retry.
     *
     * <p>{@code FAILED} is included alongside {@code PENDING}: a failed attempt rests there, visible in
     * the seller's log, until the backoff elapses and the scheduler tries it again. Ordered oldest-first
     * so a backlog drains in the order messages were queued, and bounded by the caller's limit so one
     * tick cannot try to send an unbounded batch.
     */
    @Query("""
            select n from NotificationJpaEntity n
            where n.status in ('PENDING', 'FAILED') and n.updatedAt <= :dueBefore
            order by n.createdAt asc
            """)
    List<NotificationJpaEntity> findDueForAttempt(@Param("dueBefore") Instant dueBefore, Pageable pageable);

    /**
     * Administrative search.
     *
     * <p>{@code statuses} is always a non-empty list: the caller passes every status when it wants no
     * status filter, rather than passing null. An {@code in} clause over a null collection parameter is
     * not portable across Hibernate versions and fails at query-plan time, so the empty case is
     * resolved in the adapter instead.
     */
    @Query("""
            select n from NotificationJpaEntity n
            where n.status in :statuses
              and (:type is null or n.type = :type)
              and (lower(n.subject) like :pattern escape '!'
                   or lower(n.referenceId) like :pattern escape '!')
            """)
    Page<NotificationJpaEntity> search(@Param("statuses") Collection<String> statuses,
                                       @Param("type") String type,
                                       @Param("pattern") String pattern, Pageable pageable);

    long countByStatus(String status);

    long countByStatusAndType(String status, String type);
}
