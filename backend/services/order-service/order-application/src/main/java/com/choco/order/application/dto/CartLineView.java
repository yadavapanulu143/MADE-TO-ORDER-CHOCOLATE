package com.choco.order.application.dto;

import java.util.UUID;

public record CartLineView(
        UUID productId,
        UUID variantId,
        String productName,
        String variantLabel,
        long unitPriceMinorUnits,
        String unitPrice,
        int quantity,
        long lineTotalMinorUnits,
        String lineTotal) {
}
