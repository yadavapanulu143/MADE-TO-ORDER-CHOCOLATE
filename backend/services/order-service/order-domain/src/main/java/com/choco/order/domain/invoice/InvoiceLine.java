package com.choco.order.domain.invoice;

import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import java.util.UUID;

/**
 * One priced line as it appears on an issued invoice.
 *
 * <p>A snapshot, exactly like {@link com.choco.order.domain.order.OrderLine}: the product name, variant
 * label, SKU and unit price are copied from the order at issuance and never re-read from the
 * catalogue. An invoice is a legal record of what was sold and at what price, so a later rename or
 * price change must not rewrite it.
 *
 * <p>The line total is derived from the stored unit price and quantity, never stored separately, so it
 * cannot disagree with its own parts.
 */
public record InvoiceLine(
        UUID productId,
        UUID variantId,
        String productName,
        String variantLabel,
        String sku,
        Money unitPrice,
        Quantity quantity) {

    public InvoiceLine {
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
