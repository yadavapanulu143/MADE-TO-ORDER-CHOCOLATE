package com.choco.catalog.application.dto;

import java.util.UUID;

/**
 * A variant as another service needs it to price and validate an order.
 *
 * <p>Deliberately flatter than {@link VariantView}: an order service does not need the display
 * decimal, but it does need the owning product's name and whether the product itself is on sale, so
 * one call answers both "what is this variant" and "may it be bought".
 *
 * <p>Price is the exact minor-unit integer, never a decimal string. The caller does arithmetic with
 * it — multiplying by a quantity — and a decimal would invite a rounding error into a total.
 */
public record VariantLookupView(
        UUID variantId,
        UUID productId,
        String productName,
        String variantLabel,
        String sku,
        long priceMinorUnits,
        String currency,
        int minOrderQuantity,
        int maxOrderQuantity,
        boolean variantActive,
        boolean productAvailable) {

    /** The variant may be ordered only when the variant is active and its product is on sale. */
    public boolean purchasable() {
        return variantActive && productAvailable;
    }
}
