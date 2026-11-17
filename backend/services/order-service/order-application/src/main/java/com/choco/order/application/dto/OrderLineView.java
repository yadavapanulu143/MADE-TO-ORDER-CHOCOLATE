package com.choco.order.application.dto;

import java.util.UUID;

public record OrderLineView(
        UUID productId,
        UUID variantId,
        String productName,
        String variantLabel,
        String sku,
        long unitPriceMinorUnits,
        String unitPrice,
        int quantity,
        long lineTotalMinorUnits,
        String lineTotal) {
}
