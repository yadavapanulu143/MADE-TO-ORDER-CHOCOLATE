package com.choco.order.domain.order;

import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import java.util.UUID;

/**
 * One priced line of an order.
 *
 * <p>This is a <em>snapshot</em>, not a reference. The product name, the variant label, the SKU and
 * the unit price are all copied in at the moment the order is placed, because the seller can rename a
 * product, change a price or withdraw a variant at any time, and none of that may rewrite what a
 * customer already agreed to pay. The line keeps the catalog identifiers only so the order can be
 * traced back to what was bought; it never joins to the catalogue to render itself.
 *
 * <p>The line total is derived from the stored unit price and quantity rather than stored separately,
 * so it cannot disagree with its own parts.
 */
public record OrderLine(
        UUID productId,
        UUID variantId,
        String productName,
        String variantLabel,
        String sku,
        Money unitPrice,
        Quantity quantity) {

    public OrderLine {
        if (productId == null) {
            throw new IllegalArgumentException("Product id is required");
        }
        if (variantId == null) {
            throw new IllegalArgumentException("Variant id is required");
        }
        productName = requireText(productName, "Product name", 150);
        variantLabel = requireText(variantLabel, "Variant label", 80);
        sku = normaliseSku(sku);
        if (unitPrice == null) {
            throw new IllegalArgumentException("Unit price is required");
        }
        if (unitPrice.isZero() || unitPrice.isNegative()) {
            throw new IllegalArgumentException("Unit price must be greater than zero");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity is required");
        }
    }

    /** The amount for this line, exact because both operands are integers. */
    public Money lineTotal() {
        return unitPrice.multiply(quantity.value());
    }

    private static String requireText(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String normaliseSku(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
