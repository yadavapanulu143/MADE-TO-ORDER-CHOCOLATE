package com.choco.fulfilment.application.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sets which ingredients, and how much of each, one unit of a variant consumes.
 *
 * <p>An empty line list clears the recipe. Nothing is assumed: a variant with no recipe simply
 * consumes nothing, and the made-to-order flow runs unchanged.
 */
public record ConfigureRecipeCommand(UUID variantId, List<Line> lines) {

    public record Line(UUID ingredientId, BigDecimal perUnit) {
    }
}
