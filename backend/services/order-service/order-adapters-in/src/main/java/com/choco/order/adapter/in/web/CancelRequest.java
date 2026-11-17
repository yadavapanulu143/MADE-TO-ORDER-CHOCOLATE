package com.choco.order.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for a cancellation or refund request. The reason is required and recorded. */
record CancelRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 500, message = "The reason must be at most 500 characters")
        String reason) {
}
