package com.choco.fulfilment.domain.port;

import com.choco.fulfilment.domain.inventory.RecipeLine;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port for recipes: which ingredients, and how much, one unit of a variant consumes.
 *
 * <p>A variant with no lines is a variant whose recipe has not been configured. That is a normal
 * state, not an error: the made-to-order flow runs without ingredient tracking, and the seller may
 * never configure a recipe at all.
 */
public interface RecipeRepository {

    /** The recipe lines for a variant, in a stable order. */
    List<RecipeLine> findByVariantId(UUID variantId);

    /** Replaces a variant's recipe with the given lines. Passing an empty list clears it. */
    void replaceForVariant(UUID variantId, List<RecipeLine> lines);

    boolean existsForVariant(UUID variantId);
}
