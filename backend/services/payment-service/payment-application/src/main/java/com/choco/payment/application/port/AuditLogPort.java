package com.choco.payment.application.port;

/**
 * Records security-relevant actions for later review.
 *
 * <p>Kept as a port so the use cases can state "the seller refunded payment X" without knowing where
 * that is written. A refund and a dispute are exactly the actions an operator has to be able to
 * attribute later.
 */
public interface AuditLogPort {

    void record(String actorType, String actorId, String action, String targetType, String targetId,
                String outcome, String detail, String correlationId);
}
