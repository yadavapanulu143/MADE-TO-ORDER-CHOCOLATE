package com.choco.fulfilment.application.command;

import com.choco.fulfilment.domain.inventory.UnitOfMeasure;
import java.math.BigDecimal;

/** Adds a raw material or packaging item to the seller's tracked stock. */
public record CreateIngredientCommand(String name, UnitOfMeasure unit, BigDecimal openingLevel,
                                      BigDecimal minimumLevel) {
}
