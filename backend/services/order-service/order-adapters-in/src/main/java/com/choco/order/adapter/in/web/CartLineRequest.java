package com.choco.order.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload for adding a variant to the cart.
 *
 * <p>The quantity is bounded here only against an absurd value; the variant's own configured minimum
 * and maximum are enforced by the catalogue and re-checked at checkout, because those are the seller's
 * rules and can change, whereas this bound is a sanity limit that never should.
 */
record CartLineRequest(
        @NotNull(message = "A variant id is required")
        UUID variantId,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1000, message = "Quantity is too large")
        int quantity) {
}
