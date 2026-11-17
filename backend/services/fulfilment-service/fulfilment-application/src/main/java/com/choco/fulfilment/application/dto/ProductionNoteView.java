package com.choco.fulfilment.application.dto;

import java.time.Instant;
import java.util.UUID;

/** One production note, as shown beside a job. */
public record ProductionNoteView(UUID id, String text, String authorId, Instant createdAt) {
}
