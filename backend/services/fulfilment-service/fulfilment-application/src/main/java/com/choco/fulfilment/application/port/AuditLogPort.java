package com.choco.fulfilment.application.port;

/**
 * Records security-relevant actions for later review.
 *
 * <p>Kept as a port so the use cases can state "the seller started preparation on order X" without
 * knowing where that is written. The production job and delivery records already carry their own
 * status history; the audit log is for the administrative actions around them — stock corrections,
 * coverage changes, recipe edits — so an operator can answer "who did this" without reading the
 * business tables.
 */
public interface AuditLogPort {

    void record(String actorType, String actorId, String action, String targetType, String targetId,
                String outcome, String detail, String correlationId);
}
