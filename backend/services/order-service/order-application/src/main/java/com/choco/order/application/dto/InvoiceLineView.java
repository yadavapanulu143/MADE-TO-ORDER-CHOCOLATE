package com.choco.order.application.dto;

import java.util.UUID;

/** One line of an invoice, with the exact amount and a display rendering of each money value. */
public record InvoiceLineView(
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
