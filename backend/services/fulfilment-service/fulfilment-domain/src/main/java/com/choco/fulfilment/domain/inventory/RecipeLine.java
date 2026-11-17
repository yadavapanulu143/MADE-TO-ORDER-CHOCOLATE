package com.choco.fulfilment.domain.inventory;

import java.time.Instant;
import java.util.UUID;

/**
 * A recipe line: how much of one ingredient a single unit of a variant consumes.
 *
 * <p>Stored per variant, per one unit. A recipe is deliberately not stored as a total for an order,
 * because the same variant is made in different quantities: the job scales this line by its own
 * quantity, so one definition serves every order size.
 *
 * <p>Nothing is assumed about recipes. A variant with no lines consumes nothing, and the made-to-order
 * flow runs exactly the same — this is what lets the seller leave ingredient tracking unconfigured.
 */
public record RecipeLine(
        UUID id,
        UUID variantId,
        UUID ingredientId,
        StockLevel perUnit) {

    public RecipeLine {
        if (id == null) {
            throw new IllegalArgumentException("Recipe line id is required");
        }
        if (variantId == null) {
            throw new IllegalArgumentException("Recipe line must name a variant");
        }
        if (ingredientId == null) {
            throw new IllegalArgumentException("Recipe line must name an ingredient");
        }
        if (perUnit == null || !perUnit.isPositive()) {
            throw new IllegalArgumentException("A recipe line must consume a positive amount per unit");
        }
    }

    public static RecipeLine of(UUID id, UUID variantId, UUID ingredientId, StockLevel perUnit) {
        return new RecipeLine(id, variantId, ingredientId, perUnit);
    }

    /** The amount this line consumes for a job making {@code quantity} units. */
    public StockLevel forQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        return perUnit.multiply(quantity);
    }
}
