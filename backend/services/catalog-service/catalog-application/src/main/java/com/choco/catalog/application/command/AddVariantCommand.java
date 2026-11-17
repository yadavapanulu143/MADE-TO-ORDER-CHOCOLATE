package com.choco.catalog.application.command;

import java.math.BigDecimal;

/**
 * Input for adding a variant.
 *
 * <p>The price arrives as a decimal in major units (rupees) because that is what a person types and
 * what the API documents; it is converted to minor units once, here, and never carried as a floating
 * point value.
 */
public record AddVariantCommand(
        String label,
        String sku,
        BigDecimal price,
        int minOrderQuantity,
        int maxOrderQuantity) {
}
