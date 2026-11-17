package com.choco.order.domain.order;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in an order's status history.
 *
 * <p>The order carries a single current status, but the seller, the customer and any later dispute
 * all need the path taken to reach it — when preparation actually began, when a failed attempt was
 * recorded, who moved the order. Appending an immutable entry on every transition is what makes that
 * path answerable after the fact, rather than inferred from timestamps that were overwritten.
 */
public record StatusChange(
        UUID id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String reason,
        String actorId,
        Instant occurredAt) {

    public StatusChange {
        if (id == null) {
            throw new IllegalArgumentException("Status change id is required");
        }
        if (toStatus == null) {
            throw new IllegalArgumentException("A status change must name the status entered");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("A status change must be timestamped");
        }
        reason = reason == null ? null : reason.trim();
    }

    public static StatusChange of(UUID id, OrderStatus from, OrderStatus to, String reason, String actorId,
                                  Instant at) {
        return new StatusChange(id, from, to, reason, actorId, at);
    }
}
