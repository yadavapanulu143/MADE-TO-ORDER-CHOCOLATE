package com.choco.order.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Payload for pricing and for placing an order against one of the customer's own addresses. */
record CheckoutRequest(
        @NotNull(message = "A delivery address is required")
        UUID addressId) {
}
