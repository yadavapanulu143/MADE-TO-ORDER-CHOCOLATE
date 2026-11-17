package com.choco.fulfilment.application.command;

import java.util.UUID;

/**
 * A seller-recorded delivery step.
 *
 * <p>One command for the steps that carry only a reason or a proof, rather than a command per status,
 * because the aggregate already decides which steps are legal from the current status.
 */
public record DeliveryProgressCommand(UUID orderId, DeliveryAction action, String reason,
                                      String proofReference, String notes) {

    public enum DeliveryAction {
        OUT_FOR_DELIVERY,
        DELIVERED,
        ATTEMPT_FAILED,
        RESCHEDULED,
        RETURNED,
        CANCEL
    }
}
