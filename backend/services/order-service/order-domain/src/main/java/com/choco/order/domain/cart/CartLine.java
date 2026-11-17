package com.choco.order.domain.cart;

import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import java.util.UUID;

/**
 * One line in a customer's cart.
 *
 * <p>The catalogue identifiers are the identity of the line: the same variant added twice is the same
 * line with a larger quantity, not two lines. The name, label and price are held for display only —
 * they are a snapshot the storefront can render without a catalogue round trip, and they are
 * deliberately re-read from the catalogue at checkout, because a cart can sit for days while the
 * seller changes a price.
 */
public record CartLine(
        UUID productId,
        UUID variantId,
        String productName,
        String variantLabel,
        Money unitPrice,
        Quantity quantity) {

    public CartLine {
        if (productId == null || variantId == null) {
            throw new IllegalArgumentException("A cart line needs a product and a variant");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("A cart line needs a quantity");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("A cart line needs a unit price");
        }
    }

    public CartLine withQuantity(Quantity newQuantity) {
        return new CartLine(productId, variantId, productName, variantLabel, unitPrice, newQuantity);
    }

    public CartLine withPriceAndName(Money price, String name, String label) {
        return new CartLine(productId, variantId, name, label, price, quantity);
    }

    public Money lineTotal() {
        return unitPrice.multiply(quantity.value());
    }

    /** Two lines are the same line when they are the same variant of the same product. */
    public boolean sameVariant(UUID otherProductId, UUID otherVariantId) {
        return productId.equals(otherProductId) && variantId.equals(otherVariantId);
    }
}
