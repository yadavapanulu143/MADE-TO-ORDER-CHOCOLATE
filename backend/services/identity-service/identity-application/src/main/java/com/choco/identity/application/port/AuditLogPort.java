package com.choco.identity.application.port;

/**
 * Outbound port for the security audit trail.
 *
 * <p>Implemented as a separate table, never as a log line only: an audit record must be queryable by
 * an administrator and must survive log rotation.
 */
public interface AuditLogPort {

    void record(String actorType, String actorId, String action, String targetType, String targetId,
                String outcome, String detail, String correlationId);
}
