package com.choco.order.application.command;

import java.util.UUID;

/**
 * A seller-recorded delivery step.
 *
 * <p>One command for the delivery steps that carry only a reason, rather than a command per status,
 * because the aggregate already decides which steps are legal from the current status. The
 * {@link DeliveryAction} names the intent; the aggregate refuses an action that does not apply.
 */
public record DeliveryProgressCommand(UUID orderId, DeliveryAction action, String reason) {

    public enum DeliveryAction {
        OUT_FOR_DELIVERY,
        DELIVERED,
        ATTEMPT_FAILED,
        RESCHEDULED,
        RETURNED
    }
}
