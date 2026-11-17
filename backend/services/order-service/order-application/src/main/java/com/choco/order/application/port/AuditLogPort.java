package com.choco.order.application.port;

/**
 * Records security-relevant actions for later review.
 *
 * <p>Kept as a port so the use cases can state "the seller cancelled order X" without knowing where
 * that is written. The order service's own status history already records lifecycle moves; the audit
 * log is for the administrative actions around them — refunds decided, delivery assignments, exports —
 * so an operator can answer "who did this" without reading the order tables.
 */
public interface AuditLogPort {

    void record(String actorType, String actorId, String action, String targetType, String targetId,
                String outcome, String detail, String correlationId);
}
