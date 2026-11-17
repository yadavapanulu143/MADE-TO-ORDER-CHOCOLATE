package com.choco.notification.application.port;

/**
 * Records an administrative action in the audit trail.
 *
 * <p>Identifiers only. No message body and no destination is ever passed here: the audit trail is
 * meant to answer "who did what, and when", and a customer's address or a message's contents do not
 * belong in a record that is retained and exported.
 */
public interface AuditLogPort {

    void record(String actorType, String actorId, String action, String targetType, String targetId,
                String outcome, String detail, String correlationId);
}
