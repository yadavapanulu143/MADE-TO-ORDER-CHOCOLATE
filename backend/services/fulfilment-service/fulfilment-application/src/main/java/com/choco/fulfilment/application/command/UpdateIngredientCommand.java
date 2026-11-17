package com.choco.fulfilment.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/** Changes an ingredient's low-stock threshold. */
public record UpdateIngredientCommand(UUID ingredientId, BigDecimal minimumLevel, Boolean active) {
}
