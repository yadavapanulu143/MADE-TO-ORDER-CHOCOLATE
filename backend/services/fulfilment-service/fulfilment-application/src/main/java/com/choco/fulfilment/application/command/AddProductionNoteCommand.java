package com.choco.fulfilment.application.command;

import java.util.UUID;

/** A seller's note attached to a production job. */
public record AddProductionNoteCommand(UUID jobId, String text) {
}
