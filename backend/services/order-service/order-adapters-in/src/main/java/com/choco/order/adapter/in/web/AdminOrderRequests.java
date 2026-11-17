package com.choco.order.adapter.in.web;

import com.choco.order.application.command.DeliveryProgressCommand.DeliveryAction;
import com.choco.order.application.command.PreparationProgressCommand.PreparationAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payloads for the seller's lifecycle actions on an order.
 *
 * <p>Grouped in one file because each is a handful of fields that only ever travels with its action,
 * and keeping them together makes the whole set of permitted moves visible in one place. The action
 * is an enum rather than a free-text status, so a caller can only ask for a move the domain knows
 * how to make; whether that move is legal from the order's current status is the aggregate's decision.
 */
final class AdminOrderRequests {

    private AdminOrderRequests() {
    }

    /** A preparation step: start, hold, resume, complete, packaging, ready for dispatch. */
    record PreparationRequest(
            @NotNull(message = "A preparation action is required")
            PreparationAction action,
            @Size(max = 500, message = "The reason must be at most 500 characters")
            String reason) {
    }

    /** A delivery step: out for delivery, delivered, attempt failed, rescheduled, returned. */
    record DeliveryRequest(
            @NotNull(message = "A delivery action is required")
            DeliveryAction action,
            @Size(max = 500, message = "The reason must be at most 500 characters")
            String reason) {
    }

    /** Assigns delivery responsibility to a named person. */
    record AssignDeliveryRequest(
            @NotBlank(message = "The delivery person's name is required")
            @Size(max = 120, message = "The name must be at most 120 characters")
            String deliveryPersonName,

            @Size(max = 20, message = "The phone number must be at most 20 characters")
            String deliveryPersonPhone) {
    }

    /** A seller's note attached to an order. */
    record NoteRequest(
            @NotBlank(message = "A note is required")
            @Size(max = 1000, message = "The note must be at most 1000 characters")
            String text) {
    }

    /** The seller cancels an order. */
    record CancelRequest(
            @NotBlank(message = "A reason is required")
            @Size(max = 500, message = "The reason must be at most 500 characters")
            String reason) {
    }
}
