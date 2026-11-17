package com.choco.fulfilment.domain.production;

import java.time.Instant;
import java.util.UUID;

/**
 * A free-text note the seller attaches to a production job.
 *
 * <p>Production has facts that no status captures: a batch came out darker than usual, the customer
 * asked for less sugar, a mould was swapped. Recording them beside the job keeps that context with
 * the work instead of in a separate notebook the seller has to reconcile by hand.
 */
public record ProductionNote(UUID id, String text, String authorId, Instant createdAt) {

    public static final int MAX_TEXT_LENGTH = 500;

    public ProductionNote {
        if (id == null) {
            throw new IllegalArgumentException("Note id is required");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A note must have text");
        }
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("A note must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        text = trimmed;
        if (createdAt == null) {
            throw new IllegalArgumentException("A note must be timestamped");
        }
    }

    public static ProductionNote of(UUID id, String text, String authorId, Instant at) {
        return new ProductionNote(id, text, authorId, at);
    }
}
