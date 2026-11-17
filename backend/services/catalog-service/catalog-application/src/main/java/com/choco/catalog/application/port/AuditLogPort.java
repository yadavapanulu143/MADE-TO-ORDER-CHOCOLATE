package com.choco.catalog.application.port;

/**
 * Outbound port for the catalog audit trail.
 *
 * <p>Catalog changes are administrative acts: a price change or a product withdrawal affects what
 * customers are offered, so the requirement to record who did what and when applies here as much as to
 * authentication. The row is written in its own transaction, so an operation that fails still leaves a
 * record that it was attempted.
 */
public interface AuditLogPort {

    void record(String actorType, String actorId, String action, String targetType, String targetId,
                String outcome, String detail, String correlationId);
}
