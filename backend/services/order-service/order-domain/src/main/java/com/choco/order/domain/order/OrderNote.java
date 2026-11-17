package com.choco.order.domain.order;

import java.time.Instant;
import java.util.UUID;

/**
 * A free-text note the seller attaches to an order.
 *
 * <p>Kept as an appended record rather than one mutable field so a note added during preparation is
 * not overwritten by a note added during delivery, and so each note retains who wrote it and when.
 */
public record OrderNote(UUID id, String text, String authorId, Instant createdAt) {

    private static final int MAX_TEXT = 1_000;

    public OrderNote {
        if (id == null) {
            throw new IllegalArgumentException("Note id is required");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A note must have text");
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new IllegalArgumentException("A note must be at most " + MAX_TEXT + " characters");
        }
        text = trimmed;
        if (createdAt == null) {
            throw new IllegalArgumentException("A note must be timestamped");
        }
    }
}
