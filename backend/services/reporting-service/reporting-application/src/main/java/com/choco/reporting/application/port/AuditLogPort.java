package com.choco.reporting.application.port;

/**
 * Records who exported what, for later review.
 *
 * <p>An export moves customer data out of the platform, so it is a security-relevant act that must be
 * attributable. The row records the acting administrator, the dataset, the format, the row count and
 * the window — never the exported content itself, which would defeat the point of auditing it.
 */
public interface AuditLogPort {

    void record(String actorType, String actorId, String action, String targetType, String targetId,
                String outcome, String detail, String correlationId);
}
