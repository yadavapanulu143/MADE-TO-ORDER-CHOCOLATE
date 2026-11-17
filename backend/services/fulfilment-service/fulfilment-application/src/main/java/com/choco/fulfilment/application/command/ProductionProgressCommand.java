package com.choco.fulfilment.application.command;

import java.util.UUID;

/**
 * A seller-recorded preparation step for one production job.
 *
 * <p>The action names the intent, not the target status: whether the move is legal from the job's
 * current status is the aggregate's decision, so a caller cannot force an illegal jump by naming a
 * status directly.
 */
public record ProductionProgressCommand(UUID jobId, ProductionAction action, String reason) {

    public enum ProductionAction {
        ACKNOWLEDGE,
        START,
        HOLD,
        RESUME,
        COMPLETE,
        PACKAGING,
        READY_FOR_DISPATCH,
        CANCEL
    }
}
