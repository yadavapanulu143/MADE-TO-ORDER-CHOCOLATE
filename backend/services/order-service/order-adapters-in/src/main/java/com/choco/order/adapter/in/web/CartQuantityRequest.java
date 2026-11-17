package com.choco.order.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Payload for setting a cart line's quantity to an exact value. */
record CartQuantityRequest(
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1000, message = "Quantity is too large")
        int quantity) {
}
