package com.choco.order.application.command;

import java.util.UUID;

/**
 * A seller-recorded preparation step for a made-to-order item.
 *
 * <p>The aggregate refuses a step that is not legal from the current status — for example starting
 * packaging before preparation is complete — so this command carries intent, not a target status.
 */
public record PreparationProgressCommand(UUID orderId, PreparationAction action, String reason) {

    public enum PreparationAction {
        START,
        HOLD,
        RESUME,
        COMPLETE,
        PACKAGING,
        READY_FOR_DISPATCH
    }
}
