package com.choco.identity.adapter.out.audit;

import com.choco.core.port.Clock;
import com.choco.identity.application.port.AuditLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes security audit rows.
 *
 * <p>The write runs in its own transaction ({@code REQUIRES_NEW}) so that a failed login, which is
 * deliberately rolled back, still leaves its audit row behind. If the audit write shared the
 * business transaction, a rejected attempt would erase the evidence of itself.
 *
 * <p>The row is also mirrored to the application log at {@code INFO}, because an operator watching
 * logs during an incident should not have to query the database to see a burst of failed logins.
 * Only identifiers are written to either destination.
 */
@Repository
class AuditLogAdapter implements AuditLogPort {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAdapter.class);

    interface SpringDataAuditLog extends JpaRepository<AuditLogJpaEntity, Long> {
    }

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
