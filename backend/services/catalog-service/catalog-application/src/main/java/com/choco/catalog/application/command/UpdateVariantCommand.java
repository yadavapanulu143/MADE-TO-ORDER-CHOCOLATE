package com.choco.catalog.application.command;

import java.math.BigDecimal;

/** Input for editing a variant. */
public record UpdateVariantCommand(
        String label,
        String sku,
        BigDecimal price,
        int minOrderQuantity,
        int maxOrderQuantity) {
}
