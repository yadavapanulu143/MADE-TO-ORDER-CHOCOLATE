package com.choco.fulfilment.adapter.out.audit;

import com.choco.core.port.Clock;
import com.choco.fulfilment.application.port.AuditLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes fulfilment audit rows.
 *
 * <p>The write runs in its own transaction ({@code REQUIRES_NEW}) so it survives a business
 * transaction that rolls back: a stock correction that is then rejected still leaves a record that the
 * attempt happened.
 *
 * <p>The row is mirrored to the application log at {@code INFO} so an operator watching logs does not
 * have to query the database to see a burst of administrative activity. Only identifiers are written —
 * never an address, a mobile number or anything else that would make the log a data-protection risk.
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
