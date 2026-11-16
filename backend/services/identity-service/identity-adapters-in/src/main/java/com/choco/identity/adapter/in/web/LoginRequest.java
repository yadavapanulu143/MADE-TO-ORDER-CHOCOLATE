package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code identifier} accepts either an email address or an Indian mobile number. */
public record LoginRequest(
        @NotBlank(message = "Email or mobile number is required")
        @Size(max = 254, message = "Identifier is too long")
        String identifier,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password is too long")
        String password) {
}
