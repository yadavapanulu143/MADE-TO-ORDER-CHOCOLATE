package com.choco.order.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The cart as shown to a customer.
 *
 * <p>{@code estimatedTotal} is deliberately named "estimated": the price is re-resolved from the
 * catalogue at checkout, so this is a display figure and not a commitment. The delivery charge is not
 * included here because it depends on the address, which the cart does not yet have.
 */
public record CartView(
        UUID cartId,
        List<CartLineView> lines,
        int totalItems,
        long estimatedTotalMinorUnits,
        String estimatedTotal,
        String currency,
        Instant updatedAt) {
}
