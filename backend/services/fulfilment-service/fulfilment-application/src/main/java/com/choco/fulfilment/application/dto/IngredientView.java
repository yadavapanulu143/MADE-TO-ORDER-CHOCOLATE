package com.choco.fulfilment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An ingredient as shown in the seller's stock list.
 *
 * <p>The level is rendered both as a scaled decimal and as a raw thousandths count. The decimal is for
 * a person to read; the raw count is what a client should compare and re-send, because it is exact.
 */
public record IngredientView(
        UUID id,
        String name,
        String unit,
        BigDecimal onHand,
        long onHandMilliUnits,
        BigDecimal minimumLevel,
        long minimumMilliUnits,
        boolean lowStock,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
