package com.choco.fulfilment.adapter.in.web;

import com.choco.fulfilment.application.command.DeliveryProgressCommand.DeliveryAction;
import com.choco.fulfilment.application.command.ProductionProgressCommand.ProductionAction;
import com.choco.fulfilment.domain.inventory.StockLedgerReason;
import com.choco.fulfilment.domain.inventory.UnitOfMeasure;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payloads for the seller's fulfilment actions.
 *
 * <p>Grouped in one file because each is a handful of fields that only ever travels with its action,
 * and keeping them together makes the whole set of permitted moves visible in one place. Actions are
 * enums rather than free-text statuses, so a caller can only ask for a move the domain knows how to
 * make; whether that move is legal from the current status is the aggregate's decision.
 */
final class AdminFulfilmentRequests {

    private AdminFulfilmentRequests() {
    }

    /** A preparation step: acknowledge, start, hold, resume, complete, package, ready, cancel. */
    record ProductionRequest(
            @NotNull(message = "A preparation action is required")
            ProductionAction action,
            @Size(max = 500, message = "The reason must be at most 500 characters")
            String reason) {
    }

    /** A seller's note attached to a production job. */
    record ProductionNoteRequest(
            @NotBlank(message = "A note is required")
            @Size(max = 1000, message = "The note must be at most 1000 characters")
            String text) {
    }

    /** A new raw material or packaging item to track. */
    record CreateIngredientRequest(
            @NotBlank(message = "A name is required")
            @Size(max = 120, message = "The name must be at most 120 characters")
            String name,

            @NotNull(message = "A unit of measure is required")
            UnitOfMeasure unit,

            @NotNull(message = "An opening level is required")
            @DecimalMin(value = "0.000", message = "The opening level cannot be negative")
            BigDecimal openingLevel,

            @NotNull(message = "A minimum level is required")
            @DecimalMin(value = "0.000", message = "The minimum level cannot be negative")
            BigDecimal minimumLevel) {
    }

    /** Changes an ingredient's low-stock threshold, or retires it from the active list. */
    record UpdateIngredientRequest(
            @NotNull(message = "A minimum level is required")
            @DecimalMin(value = "0.000", message = "The minimum level cannot be negative")
            BigDecimal minimumLevel,

            Boolean active) {
    }

    /** A manual stock movement: a delivery received, a stock-take correction, or a write-off. */
    record AdjustStockRequest(
            @NotNull(message = "An amount is required")
            BigDecimal amount,

            @NotNull(message = "A reason is required")
            StockLedgerReason reason,

            @Size(max = 500, message = "The note must be at most 500 characters")
            String note) {
    }

    /** Sets which ingredients, and how much of each, one unit of a variant consumes. */
    record ConfigureRecipeRequest(
            @NotNull(message = "A line list is required (send an empty list to clear the recipe)")
            List<RecipeLineRequest> lines) {

        record RecipeLineRequest(
                @NotNull(message = "An ingredient id is required")
                UUID ingredientId,

                @NotNull(message = "A per-unit amount is required")
                @DecimalMin(value = "0.001", message = "The per-unit amount must be greater than zero")
                BigDecimal perUnit) {
        }
    }

    /** Assigns delivery responsibility to a named person the seller manages. */
    record AssignDeliveryRequest(
            @NotBlank(message = "The delivery person's name is required")
            @Size(max = 120, message = "The name must be at most 120 characters")
            String deliveryPersonName,

            @Size(max = 20, message = "The phone number must be at most 20 characters")
            String deliveryPersonPhone) {
    }

    /**
     * A seller-recorded delivery step.
     *
     * <p>The proof reference is a reference to evidence the seller holds — a note of who signed, or a
     * key to an uploaded image — never the image itself and never a fabricated tracking number. There
     * is no courier here, so there is nothing to invent.
     */
    record DeliveryProgressRequest(
            @NotNull(message = "A delivery action is required")
            DeliveryAction action,

            @Size(max = 500, message = "The reason must be at most 500 characters")
            String reason,

            @Size(max = 500, message = "The proof reference must be at most 500 characters")
            String proofReference,

            @Size(max = 500, message = "The notes must be at most 500 characters")
            String notes) {
    }

    /**
     * Creates or updates a delivery area the seller covers.
     *
     * <p>The prefix is an exact six-digit PIN code or a shorter prefix of one, so a seller who covers a
     * whole city can enter it once. The charge is in paise, matching every other money field on the
     * API.
     */
    record ServiceableAreaRequest(
            @NotBlank(message = "A PIN code or prefix is required")
            @Pattern(regexp = "^[1-9][0-9]{0,5}$",
                    message = "Enter a PIN code or a leading prefix of one (1 to 6 digits, not starting with 0)")
            String pincodePrefix,

            @Size(max = 120, message = "The area name must be at most 120 characters")
            String areaName,

            @NotNull(message = "A delivery charge is required")
            @Min(value = 0, message = "The delivery charge cannot be negative")
            Long deliveryChargeMinorUnits,

            Boolean active) {
    }
}
