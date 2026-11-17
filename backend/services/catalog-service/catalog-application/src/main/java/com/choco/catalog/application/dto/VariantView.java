package com.choco.catalog.application.dto;

import java.util.UUID;

/**
 * A variant as presented to a caller.
 *
 * <p>Price is exposed both as minor units (for exact arithmetic by another service) and as a decimal
 * string in major units (for display). The decimal is a string, not a {@code double}, so a JSON
 * consumer cannot introduce a floating-point rounding error when it re-reads the value.
 */
public record VariantView(
        UUID id,
        String label,
        String sku,
        long priceMinorUnits,
        String price,
        String currency,
        int minOrderQuantity,
        int maxOrderQuantity,
        boolean active) {
}
