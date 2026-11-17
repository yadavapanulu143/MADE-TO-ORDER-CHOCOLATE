package com.choco.catalog.adapter.out.audit;

import com.choco.catalog.application.port.AuditLogPort;
import com.choco.core.port.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes catalog audit rows.
 *
 * <p>The write runs in its own transaction ({@code REQUIRES_NEW}) so it survives a business
 * transaction that rolls back. A rejected edit — a price change refused by an invariant, say — still
 * leaves a record that it was attempted, which is what an investigator needs when a catalogue looks
 * wrong and nobody admits to changing it.
 *
 * <p>The row is mirrored to the application log at {@code INFO} so an operator watching logs does not
 * have to query the database to see a burst of catalogue changes. Only identifiers are written.
 */
@Repository
class AuditLogAdapter implements AuditLogPort {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAdapter.class);

    private final SpringDataAuditLog delegate;
    private final Clock clock;

    AuditLogAdapter(SpringDataAuditLog delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorType, String actorId, String action, String targetType,
                       String targetId, String outcome, String detail, String correlationId) {
        AuditLogJpaEntity entity = new AuditLogJpaEntity();
        entity.setOccurredAt(clock.now());
        entity.setActorType(actorType);
        entity.setActorId(actorId);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setOutcome(outcome);
        entity.setDetail(truncate(detail));
        entity.setCorrelationId(correlationId);
        delegate.save(entity);

        log.info("audit actor={}:{} action={} target={}:{} outcome={} detail={}",
                actorType, actorId, action, targetType, targetId, outcome, detail);
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
