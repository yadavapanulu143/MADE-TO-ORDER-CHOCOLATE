package com.choco.reporting.adapter.out.audit;

import com.choco.core.port.Clock;
import com.choco.reporting.application.port.AuditLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes reporting-service audit rows.
 *
 * <p>The write runs in its own transaction so an export that is recorded is recorded even if the
 * surrounding request later fails for an unrelated reason; the act of pulling data out happened and
 * must be visible.
 *
 * <p>Only identifiers and a row count are written — never the exported content, which would make the
 * audit table itself the leak the export was meant to control.
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
