package com.choco.fulfilment.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A variant's recipe: which ingredients, and how much of each, one unit consumes. */
public record RecipeView(UUID variantId, List<Line> lines) {

    public record Line(UUID ingredientId, String ingredientName, String unit, BigDecimal perUnit,
                       long perUnitMilliUnits) {
    }
}
